package com.sunder.juxtapose.common;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.io.resource.FileResource;
import cn.hutool.core.io.resource.Resource;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;

import java.io.File;
import java.nio.file.Paths;

/**
 * @author : denglinhai
 * @date : 20:19 2025/08/14
 */
public class MultiProtocolResource {
    private final static String JUXTAPOSE_HOME_ENV = "JUXTAPOSE_HOME";

    private final boolean useVariable;
    private Resource resource;

    /**
     * 先从绝对路径或者file协议开头获取，如果使用变量则先从JUXTAPOSE_HOME项目路径下看是否存在，然后再从classpath里找
     *
     * @param path 路径
     * @param useVariable 是否从固定变量里找
     */
    public MultiProtocolResource(String path, boolean useVariable) {
        this.useVariable = useVariable;

        if (StrUtil.isNotBlank(path)) {
            if (path.startsWith(URLUtil.FILE_URL_PREFIX) || FileUtil.isAbsolutePath(path)) {
                this.resource = new FileResource(path);
            }
        }

        if (useVariable) {
            File file = Paths.get(getVariable()).resolve(path).toFile();
            this.resource = new FileResource(file);
        } else {
            this.resource = new ClassPathResource(path);
        }
    }

    public Resource getResource() {
        return resource;
    }

    /**
     * @return 获取项目所在的根目录
     */
    private String getVariable() {
        // Java参数里找
        String varValue = System.getProperty(JUXTAPOSE_HOME_ENV);
        // 环境变量中查找
        if (null == varValue) {
            varValue = System.getenv(JUXTAPOSE_HOME_ENV);
        }

        return varValue;
    }
}
