
package com.maplecloudy.distribute.engine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.LocalResourceType;
import org.apache.hadoop.yarn.api.records.LocalResourceVisibility;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.client.api.YarnClientApplication;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.apache.hadoop.yarn.util.Records;
import org.codehaus.jettison.json.JSONObject;

import com.maplecloudy.distribute.engine.app.elasticsearch.appmaster.ApplicationMaster;
import com.maplecloudy.distribute.engine.utils.PropertiesUtils;
import com.maplecloudy.distribute.engine.utils.YarnCompat;
import com.maplecloudy.distribute.engine.utils.YarnUtils;

public class ClusterEngineClient {

    private static final Log log = LogFactory.getLog(ClusterEngineClient.class);

    private final YarnClient client;
    private JSONObject para;

    public ClusterEngineClient(YarnClient client, JSONObject para) {
        this.client = client;
        this.para =para;
    }

    public ApplicationId run() throws YarnException, IOException {
        client.start();

        YarnClientApplication app = client.createApplication();
        ApplicationSubmissionContext am = setupAM(app);
        ApplicationId appId = client.submitApplication(am);
        return am.getApplicationId();
    }

    private ApplicationSubmissionContext setupAM(YarnClientApplication clientApp) {
        ApplicationSubmissionContext appContext = clientApp.getApplicationSubmissionContext();
        // already happens inside Hadoop but to be consistent
        appContext.setApplicationId(clientApp.getNewApplicationResponse().getApplicationId());
        appContext.setApplicationName(para);
        appContext.setAMContainerSpec(createContainerContext());
        appContext.setResource(YarnCompat.resource(client.getConfiguration(), clientCfg.amMem(), clientCfg.amVCores()));
        appContext.setPriority(Priority.newInstance(clientCfg.amPriority()));
        appContext.setQueue(clientCfg.amQueue());
        appContext.setApplicationType(clientCfg.appType());
        appContext.setMaxAppAttempts(1);
        appContext.setKeepContainersAcrossApplicationAttempts(true);
        YarnCompat.setApplicationTags(appContext, clientCfg.appTags());

        return appContext;
    }

    private ContainerLaunchContext createContainerContext() {
        ContainerLaunchContext amContainer = Records.newRecord(ContainerLaunchContext.class);

        amContainer.setLocalResources(setupEsYarnJar());
        amContainer.setEnvironment(setupEnv());
        amContainer.setCommands(setupCmd());

        return amContainer;
    }

    private Map<String, LocalResource> setupEsYarnJar() {
        Map<String, LocalResource> resources = new LinkedHashMap<String, LocalResource>();
        LocalResource esYarnJar = Records.newRecord(LocalResource.class);
        Path p = new Path(clientCfg.jarHdfsPath());
        FileStatus fsStat;
        try {
            fsStat = FileSystem.get(client.getConfiguration()).getFileStatus(p);
        } catch (IOException ex) {
            throw new IllegalArgumentException(
                    String.format("Cannot find jar [%s]; make sure the artifacts have been properly provisioned and the correct permissions are in place.", clientCfg.jarHdfsPath()), ex);
        }
        // use the normalized path as otherwise YARN chokes down the line
        esYarnJar.setResource(ConverterUtils.getYarnUrlFromPath(fsStat.getPath()));
        esYarnJar.setSize(fsStat.getLen());
        esYarnJar.setTimestamp(fsStat.getModificationTime());
        esYarnJar.setType(LocalResourceType.FILE);
        esYarnJar.setVisibility(LocalResourceVisibility.PUBLIC);

        resources.put(clientCfg.jarName(), esYarnJar);
        return resources;
    }

    private Map<String, String> setupEnv() {
        Configuration cfg = client.getConfiguration();

        Map<String, String> env = YarnUtils.setupEnv(cfg);
        YarnUtils.addToEnv(env, EsYarnConstants.CFG_PROPS, PropertiesUtils.propsToBase64(clientCfg.asProperties()));
        YarnUtils.addToEnv(env, clientCfg.envVars());

        return env;
    }

    private List<String> setupCmd() {
      
        List<String> cmds = new ArrayList<String>();
//        cmds.add("sleep 600 \n");
        // don't use -jar since it overrides the classpath
        cmds.add(YarnCompat.$$(ApplicationConstants.Environment.JAVA_HOME) + "/bin/java "
        +ApplicationMaster.class.getName()
        +" 1>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/" + ApplicationConstants.STDOUT
        +" 2>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/" + ApplicationConstants.STDERR);
        return cmds;
    }
}