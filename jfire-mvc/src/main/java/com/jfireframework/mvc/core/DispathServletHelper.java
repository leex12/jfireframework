package com.jfireframework.mvc.core;

import java.io.File;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.jfireframework.baseutil.exception.JustThrowException;
import com.jfireframework.baseutil.exception.UnSupportException;
import com.jfireframework.baseutil.simplelog.ConsoleLogFactory;
import com.jfireframework.baseutil.simplelog.Logger;
import com.jfireframework.codejson.JsonObject;
import com.jfireframework.mvc.config.MvcStaticConfig;
import com.jfireframework.mvc.core.action.Action;
import com.jfireframework.mvc.core.action.ActionCenter;
import com.jfireframework.mvc.core.action.ActionCenterBulder;
import com.jfireframework.mvc.util.FileChangeDetect;

public class DispathServletHelper
{
    private static final Logger     logger = ConsoleLogFactory.getLogger();
    private ActionCenter            actionCenter;
    private final ServletContext    servletContext;
    private final RequestDispatcher staticResourceDispatcher;
    private final JsonObject        config;
    private final FileChangeDetect  detect;
    private final boolean           devMode;
    private final String            encode;
    
    public DispathServletHelper(ServletContext servletContext, JsonObject config)
    {
        this.servletContext = servletContext;
        staticResourceDispatcher = getStaticResourceDispatcher();
        this.config = config;
        encode = config.getWString("encode") == null ? "UTF8" : config.getWString("encode");
        devMode = config.contains("devMode") ? config.getBoolean("devMode") : false;
        if (devMode)
        {
            String reloadPath = config.getWString("reloadPath");
            detect = new FileChangeDetect(new File(reloadPath));
            actionCenter = ActionCenterBulder.generate(config, servletContext, encode);
        }
        else
        {
            detect = null;
            actionCenter = ActionCenterBulder.generate(config, servletContext, encode);
        }
        
    }
    
    private RequestDispatcher getStaticResourceDispatcher()
    {
        RequestDispatcher requestDispatcher = null;
        if ((requestDispatcher = servletContext.getNamedDispatcher(MvcStaticConfig.COMMON_DEFAULT_SERVLET_NAME)) != null)
        {
        }
        else if ((requestDispatcher = servletContext.getNamedDispatcher(MvcStaticConfig.RESIN_DEFAULT_SERVLET_NAME)) != null)
        {
        }
        else if ((requestDispatcher = servletContext.getNamedDispatcher(MvcStaticConfig.WEBLOGIC_DEFAULT_SERVLET_NAME)) != null)
        {
        }
        else if ((requestDispatcher = servletContext.getNamedDispatcher(MvcStaticConfig.WEBSPHERE_DEFAULT_SERVLET_NAME)) != null)
        {
        }
        else
        {
            throw new UnSupportException("找不到默认用来处理静态资源的处理器");
        }
        return requestDispatcher;
    }
    
    public String encode()
    {
        return encode;
    }
    
    public Action getAction(HttpServletRequest request)
    {
        return actionCenter.getAction(request);
    }
    
    public void handleStaticResourceRequest(HttpServletRequest request, HttpServletResponse response)
    {
        try
        {
            staticResourceDispatcher.forward(request, response);
        }
        catch (Exception e)
        {
            throw new JustThrowException(e);
        }
    }
    
    public void preHandleDevMode()
    {
        if (devMode)
        {
            if (detect.detectChange())
            {
                long t0 = System.currentTimeMillis();
                actionCenter = ActionCenterBulder.generate(config, servletContext, encode);
                logger.debug("热部署,耗时:{}", System.currentTimeMillis() - t0);
            }
        }
    }
}
