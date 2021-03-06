package com.company.framework.servlet;

import com.company.framework.annotation.Autowired;
import com.company.framework.annotation.Controller;
import com.company.framework.annotation.RequestMapping;
import com.company.framework.annotation.Service;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class DispatcherServlet extends HttpServlet {

    Properties props = new Properties();
    List<String> classNames = new ArrayList<String>();
    Map<String, Object> containerIOC = new HashMap<String, Object>();
    Map<String, Method> handlerMapping = new HashMap<String, Method>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        //1-读取配置文件中的包扫描信息
        loadConfig(config.getInitParameter("contextConfigLocation").split(":")[1]);
//        System.out.println(props.getProperty("scanPackage"));
        //2-扫描出指定路径下的所有Class文件
        componentScan(props.getProperty("scanPackage"));
//        for(String className:classNames) {
//            System.out.println(className);
//        }
        //3-初始化IOC容器，处理@Controller @Service
        initIOC();
//        for(Map.Entry<String,Object> entry:containerIOC.entrySet()){
//            System.out.println(entry.getKey()+"--->"+entry.getValue());
//        }
        //4-实现AutoWired依赖注入
        autowired();
        //5-实现HandlerMapping（key:value）  key=url   value=method的初始化
        initHandlerMapping();
//        for (Map.Entry<String, Method> entry : handlerMapping.entrySet()) {
//            System.out.println("********" + entry.getKey() + "<---->" + entry.getValue().getName());
//        }
    }


    private void loadConfig(String contextConfigLocation) {
        InputStream in = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            props.load(in);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    private void componentScan(String scanPackage) {
        //递归  知道了一个文件夹，把符合条件的文件从主目录及子文件夹中获得
        //scanPackage = com.company.oasis
        URL url = this.getClass().getClassLoader().getResource(scanPackage.replaceAll("\\.", "/"));
        File classDir = new File(url.getFile());
        for (File file : classDir.listFiles()) {
            if (file.isDirectory()) {
                componentScan(scanPackage + "." + file.getName());
            } else {
                if (file.getName().endsWith(".class")) {
                    classNames.add(scanPackage + "." + file.getName().replace(".class", "").trim());
                }
            }
        }

    }

    public String initBeanName(String beanName) {
        char[] chars = beanName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    private void initIOC() {
        if (!classNames.isEmpty()) {
            for (String className : classNames) {
                try {
                    Class<?> clazz = Class.forName(className);
                    if (clazz.isAnnotationPresent(Controller.class)) {
                        Controller controller = clazz.getAnnotation(Controller.class);
                        String actionName = controller.value();
                        if ("".equals(actionName)) {
                            actionName = initBeanName(clazz.getSimpleName());
                        }
                        containerIOC.put(actionName, clazz.newInstance());
                    } else if (clazz.isAnnotationPresent(Service.class)) {
                        Service service = clazz.getAnnotation(Service.class);
                        String serviceName = service.value();
                        if ("".equals(serviceName)) {
                            serviceName = initBeanName(clazz.getSimpleName());
                        }
                        containerIOC.put(serviceName, clazz.newInstance());
                        //为@Autowired编写IOC容器的注入
                        Class<?>[] interfaces = clazz.getInterfaces();
                        for (Class<?> inter : interfaces) {
                            if (inter.getSimpleName().endsWith("Service")) {
                                containerIOC.put(inter.getName(), clazz.newInstance());
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }


    private void autowired() {
        if (!containerIOC.isEmpty()) {
            for (Map.Entry<String, Object> entry : containerIOC.entrySet()) {
                Object bean = entry.getValue();
                Field[] fields = bean.getClass().getDeclaredFields();
                for (Field field : fields) {
                    if (field.isAnnotationPresent(Autowired.class)) {
                        Autowired autowired = field.getAnnotation(Autowired.class);
                        String beanName = autowired.value();
                        if ("".equals(beanName)) {
                            beanName = field.getType().getName();
                        }
                        field.setAccessible(true);
                        try {
                            field.set(bean, containerIOC.get(beanName));
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

    }


    private void initHandlerMapping() {
        if (!containerIOC.isEmpty()) {
            for (Map.Entry<String, Object> entry : containerIOC.entrySet()) {
                Object bean = entry.getValue();
                Class<?> clazz = bean.getClass();
                String baseUrl = "";
                if (clazz.isAnnotationPresent(Controller.class) &&
                        clazz.isAnnotationPresent(RequestMapping.class)) {
                    RequestMapping rm = clazz.getAnnotation(RequestMapping.class);
                    baseUrl += "/" + rm.value();
                    Method[] methods = clazz.getDeclaredMethods();
                    for (Method method : methods) {
                        if (method.isAnnotationPresent(RequestMapping.class)) {
                            RequestMapping uri = method.getAnnotation(RequestMapping.class);
                            baseUrl += "/" + uri.value();
                        }
//                        System.out.println(baseUrl);
                        handlerMapping.put(baseUrl.replaceAll("/+","/").trim(), method);
                    }
                }
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if(!handlerMapping.isEmpty()){

            //1-截取用户请求的uri
            String url = req.getRequestURI();
            String contextPath = req.getContextPath();
            String uriKey = url.replace(contextPath,"")
                    .replaceAll("/+","/");
//            System.out.println(uriKey);

            //2-与handlerMapping进行匹配，找到Method方法
            if(handlerMapping.containsKey(uriKey)){
                //3-执行Method方法
                Method method = handlerMapping.get(uriKey);
                //从IOC容器中获得类的对象
                String beanName = initBeanName(method.getDeclaringClass().getSimpleName());
                Object obj = containerIOC.get(beanName);
                //获得用户传来的请求参数
                Map<String,String[]> userParams = req.getParameterMap();
                String[] params = new String[userParams.size()];
                int i=0;
                for(String[] param:userParams.values()){
                    params[i] = param[0];
                    i++;
                }
                System.out.println(Arrays.asList(params));
                //执行方法
                method.invoke(obj,params);
            }else{
                resp.getWriter().println("404:no uri match in HandlerMapping");
            }

        }
    }
}
