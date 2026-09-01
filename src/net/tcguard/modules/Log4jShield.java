package net.tcguard.modules;

import net.tcguard.TCGuard;
import org.bukkit.Bukkit;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.logging.Filter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class Log4jShield {

    private final TCGuard plugin;
    private static final Pattern LOG4J_PATTERN = Pattern.compile(
            "(?i)" +
            "(" +
                "\\$\\{\\s*j\\s*n\\s*d\\s*i\\s*:" +
                "|" +
                "\\$\\{[^}]*\\$\\{[^}]*(lower|upper|env|sys|java|date|bundle|ctx|base64|url|main|marker|spring|docker|k8s)" +
                "|" +
                "\\$\\{\\s*(env|sys|java|date|bundle|ctx|main|marker|spring|jndi|base64|url|docker|k8s)\\s*:" +
                "|" +
                "\\$\\{.*?(j|\\%6a|\\u006a).*(n|\\%6e|\\u006e).*(d|\\%64|\\u0064).*(i|\\%69|\\u0069)\\s*:" +
            ")"
    );

    public Log4jShield(TCGuard plugin) {
        this.plugin = plugin;
    }

    public void init() {
        // 1. Set JVM System Flags
        try {
            System.setProperty("log4j2.formatMsgNoLookups", "true");
            System.setProperty("log4j.formatMsgNoLookups", "true");
        } catch (Throwable ignored) {}

        // 2. Install Root Java Util Logging Filter
        installJulFilter();

        // 3. Install Apache Log4j 2 Root Logger Filter via Reflection
        installLog4j2Filter();
    }

    public static boolean containsLog4jExploit(String text) {
        if (text == null || text.isEmpty()) return false;
        if (!text.contains("$") && !text.contains("%") && !text.contains("\\u00")) return false;
        return LOG4J_PATTERN.matcher(text).find();
    }

    private void installJulFilter() {
        try {
            Filter julFilter = new Filter() {
                @Override
                public boolean isLoggable(LogRecord record) {
                    if (record == null) return true;
                    if (record.getMessage() != null && containsLog4jExploit(record.getMessage())) {
                        return false;
                    }
                    if (record.getParameters() != null) {
                        for (Object param : record.getParameters()) {
                            if (param != null && containsLog4jExploit(param.toString())) {
                                return false;
                            }
                        }
                    }
                    return true;
                }
            };

            Logger root = Logger.getLogger("");
            if (root != null) {
                root.setFilter(julFilter);
            }

            Logger serverLogger = Bukkit.getLogger();
            if (serverLogger != null) {
                serverLogger.setFilter(julFilter);
            }
        } catch (Throwable e) {
            plugin.getLogger().fine("[Log4jShield] JUL filter warning: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void installLog4j2Filter() {
        try {
            Class<?> logManagerClass = Class.forName("org.apache.logging.log4j.LogManager");
            Class<?> filterClass = Class.forName("org.apache.logging.log4j.core.Filter");
            Class<?> resultClass = Class.forName("org.apache.logging.log4j.core.Filter$Result");
            Class<?> lifeCycleStateClass = null;
            try {
                lifeCycleStateClass = Class.forName("org.apache.logging.log4j.core.LifeCycle$State");
            } catch (Throwable ignored) {}

            final Object resultDeny = Enum.valueOf((Class<Enum>) resultClass, "DENY");
            final Object resultNeutral = Enum.valueOf((Class<Enum>) resultClass, "NEUTRAL");
            final Object stateStarted = lifeCycleStateClass != null ? Enum.valueOf((Class<Enum>) lifeCycleStateClass, "STARTED") : null;

            InvocationHandler handler = (proxy, method, args) -> {
                String mName = method.getName();
                if ("filter".equals(mName)) {
                    if (args != null) {
                        for (Object arg : args) {
                            if (arg == null) continue;
                            if (arg instanceof String) {
                                if (containsLog4jExploit((String) arg)) {
                                    return resultDeny;
                                }
                            } else {
                                String str = arg.toString();
                                if (str != null && containsLog4jExploit(str)) {
                                    return resultDeny;
                                }
                            }
                        }
                    }
                    return resultNeutral;
                }
                if ("getOnMatch".equals(mName) || "getOnMismatch".equals(mName)) {
                    return resultNeutral;
                }
                if ("getState".equals(mName)) {
                    return stateStarted;
                }
                if ("isStarted".equals(mName)) {
                    return true;
                }
                if ("isStopped".equals(mName)) {
                    return false;
                }
                if ("start".equals(mName) || "stop".equals(mName) || "initialize".equals(mName)) {
                    return null;
                }
                return null;
            };

            Object filterProxy = Proxy.newProxyInstance(filterClass.getClassLoader(), new Class<?>[]{filterClass}, handler);

            Method getRootLoggerMethod = logManagerClass.getMethod("getRootLogger");
            Object rootLogger = getRootLoggerMethod.invoke(null);

            if (rootLogger != null) {
                try {
                    Method addFilterMethod = rootLogger.getClass().getMethod("addFilter", filterClass);
                    addFilterMethod.invoke(rootLogger, filterProxy);
                    plugin.getLogger().info("[Log4jShield] Log4j 2 Core Root Filter successfully hooked.");
                } catch (NoSuchMethodException e) {
                    // Try logger context approach
                    Class<?> loggerContextClass = Class.forName("org.apache.logging.log4j.core.LoggerContext");
                    Method getContextMethod = logManagerClass.getMethod("getContext", boolean.class);
                    Object context = getContextMethod.invoke(null, false);
                    if (context != null) {
                        Method getConfigurationMethod = context.getClass().getMethod("getConfiguration");
                        Object config = getConfigurationMethod.invoke(context);
                        if (config != null) {
                            Method addFilterMethod = config.getClass().getMethod("addFilter", filterClass);
                            addFilterMethod.invoke(config, filterProxy);
                            plugin.getLogger().info("[Log4jShield] Log4j 2 Configuration Filter successfully hooked.");
                        }
                    }
                }
            }
        } catch (ClassNotFoundException ignored) {
            // Server is running without Log4j2 (e.g. Modern Paper with SLF4J / custom backend)
        } catch (Throwable t) {
            plugin.getLogger().fine("[Log4jShield] Log4j2 filter setup note: " + t.getMessage());
        }
    }
}
