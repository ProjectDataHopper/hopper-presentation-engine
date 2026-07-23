package org.hopper.rest.servlet;

import org.hopper.rest.HRest;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class HServletContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event){
        HRest hopperRest = HRest.getInstance();
    }

}
