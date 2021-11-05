package org.vaadin.googleanalytics.tracking;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vaadin.googleanalytics.tracking.EnableGoogleAnalytics.LogLevel;
import org.vaadin.googleanalytics.tracking.EnableGoogleAnalytics.SendMode;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.internal.ExecutionContext;
import com.vaadin.flow.internal.JsonCodec;
import com.vaadin.flow.internal.JsonUtils;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.shared.ui.LoadMode;

import elemental.json.JsonObject;
import elemental.json.impl.JreJsonFactory;

/**
 * Sends commands to Google Analytics in the browser. An instance of the tracker
 * can be retrieved from a given UI instance ({@link #get(UI)}) or for the
 * current UI instance ({@link #getCurrent()}).
 * <p>
 * Page view commands will automatically be sent for any Flow navigation if the
 * tracker can be configured.
 * <p>
 * The first time any command is sent, the tracker will configure itself based
 * on the top-level router layout in the corresponding UI. The layout should be
 * annotated with @{@link EnableGoogleAnalytics} or implement
 * {@link TrackerConfigurator} for the configuration to succeed.
 */
public class GoogleAnalyticsTracker {
	
	public static final Logger LOGGER = LoggerFactory.getLogger(GoogleAnalyticsTracker.class);
	
	/*
	 <!-- Global site tag (gtag.js) - Google Analytics -->
<script async src="https://www.googletagmanager.com/gtag/js?id=G-99W23BCJ56"></script>
<script>
  window.dataLayer = window.dataLayer || [];
  function gtag(){dataLayer.push(arguments);}
  gtag('js', new Date());

  gtag('config', 'G-99W23BCJ56');
  
    window.dataLayer = window.dataLayer || [];function gtag(){dataLayer.push(arguments);}gtag('js', new Date());gtag('config', 'G-99W23BCJ56');
    
    
</script>
</script>

//debug
 gtag('config', 'G-12345ABCDE',{'debug_mode':true});
	 */
	
    private final UI ui;

    private boolean inited = false;

    private String pageViewPrefix = "";

    //protected String gtagScript = "window.dataLayer = window.dataLayer || [];console.log('setup datalayer: '+window.dataLayer);function gtag(){dataLayer.push(arguments);}console.log('setup gtag: '+gtag);";
    //protected String gtagScript = "window.dataLayer = window.dataLayer || [];console.log('setup datalayer: '+window.dataLayer);var gtag = function(){dataLayer.push(arguments);};console.log('setup gtag: '+gtag);";
    //protected String executeScript = "window.dataLayer = window.dataLayer || [];function gtag(){dataLayer.push(arguments);}gtag('js', new Date());gtag('config', '%1s',{'debug_mode':%2s});";
    protected String debugScript = "console.log(window.gtag);console.log('debug: '+window.dataLayer);window.gtag('js', new Date());window.gtag('config', '%1s',{'debug_mode':%2s});";
    
    protected TrackerConfiguration configuration;
    
    protected List<TrackerInitListener> initListeners = new ArrayList<TrackerInitListener>();
    
    /**
     * List of actions to send before the next Flow response is created.
     * Initialization can only happen after routing has completed since the
     * top-level layout can only be identified at that point. This queue is only
     * needed for actions that are issues before initialization has happened,
     * but it is still used in all cases to keep the internal logic simpler.
     */
    private ArrayList<Serializable[]> pendingEvents = new ArrayList<>();
    
    //private ArrayList<Serializable[]> pendingEvents = new ArrayList<>();

    private GoogleAnalyticsTracker(UI ui) {
        this.ui = ui;
    }

    /**
     * Gets or creates a tracker for the current UI.
     * 
     * @see UI#getCurrent()
     * 
     * @return the tracker for the current UI, or <code>null</code> if there is
     *         no current UI
     */
    public static GoogleAnalyticsTracker getCurrent() {
        UI ui = UI.getCurrent();
        if (ui == null) {
            return null;
        }
        return get(ui);
    }

    /**
     * Gets or creates a tracker for the given UI.
     * 
     * @param ui
     *            the UI for which to get at tracker, not <code>null</code>
     * @return the tracker for the given ui
     */
    public static GoogleAnalyticsTracker get(UI ui) {
        GoogleAnalyticsTracker tracker = ComponentUtil.getData(ui, GoogleAnalyticsTracker.class);
        if (tracker == null) {
            tracker = new GoogleAnalyticsTracker(ui);
            ComponentUtil.setData(ui, GoogleAnalyticsTracker.class, tracker);
        }
        return tracker;
    }

    public void addInitListener(TrackerInitListener l) {
    	if(!inited) initListeners.add(l);
    }
    
    public boolean init() {
    	
    	if(!inited) {
    	
	    	LOGGER.warn("info");
	    	
	        configuration = createConfig(ui);
	        
	        LOGGER.warn("config: {}",configuration);
	
	        if (configuration == null) {
				/*throw new IllegalStateException(
				        "There are pending actions for a tracker that isn't initialized and cannot be initialized automatically. Ensure there is a @"
				                + EnableGoogleAnalytics.class.getSimpleName()
				                + " on the application's main layout or that it implements "
				                + TrackerConfigurator.class.getSimpleName() + ".");*/
	        	LOGGER.info("Could not be inited");
	        	
	        }
	        else {
	
		        String trackingId = configuration.getTrackingId();
		        if (trackingId == null || trackingId.isEmpty()) {
		            throw new IllegalStateException("No tracking id has been defined.");
		        }
		
		        pageViewPrefix = configuration.getPageViewPrefix();
		
		        
		        try {
					ui.getPage().executeJavaScript(IOUtils.toString(GoogleAnalyticsTracker.class.getResourceAsStream("gtag.js"),Charset.defaultCharset()));
				}
		        catch (IOException e) {
				}
		        
		        String execute = String.format(debugScript, configuration.getTagId(),configuration.getDebugMode());
		        LOGGER.info("execute: {}",execute);
		        ui.getPage().executeJavaScript(execute);
		
				/*Map<String, Serializable> gaDebug = config.getGaDebug();
				if (!gaDebug.isEmpty()) {
				    ui.getPage().executeJavaScript("window.ga_debug = $0;", toJsonObject(gaDebug));
				}*/
		
		        //sendAction(createAction("create", configuration.getCreateFields(), trackingId, configuration.getCookieDomain()));
		
		        Map<String, Serializable> initialValues = configuration.getInitialValues();
		        if (!initialValues.isEmpty()) {
		            //sendAction(createAction("set", initialValues));
		        }
		
		        ui.getPage().addJavaScript(configuration.getScriptUrl(), LoadMode.LAZY);
		        
		        
		        inited = true;
		        
		        this.initListeners.forEach(l -> l.init(this));
		        
		        sendEvents();
		        
	        }
    	}
    	return inited;
    }

    private static TrackerConfiguration createConfig(UI ui) {
        TrackerConfiguration config = null;

   
        HasElement routeLayout = findRouteLayout(ui);
        LOGGER.info("routeLayout: {}",routeLayout);
        boolean productionMode = ui.getSession().getConfiguration().isProductionMode();

        EnableGoogleAnalytics annotation = routeLayout.getClass().getAnnotation(EnableGoogleAnalytics.class);

        if (annotation != null) {
            config = TrackerConfiguration.fromAnnotation(annotation, productionMode);
        }

        if (routeLayout instanceof TrackerConfigurator) {
            if (config == null) {
                // Use same defaults as in the annotation
                LogLevel logLevel = productionMode ? LogLevel.NONE : LogLevel.DEBUG;
                boolean sendHits = SendMode.PRODUCTION.shouldSend(productionMode);

                config = TrackerConfiguration.create(logLevel, sendHits);
            }

            ((TrackerConfigurator) routeLayout).configureTracker(config);
        }

        return config;
    }

    private static HasElement findRouteLayout(UI ui) {
        List<HasElement> routeChain = ui.getInternals().getActiveRouterTargetsChain();
        if (routeChain.isEmpty()) {
            throw new IllegalStateException("Cannot initialize when no router target is active");
        }
        return routeChain.get(routeChain.size() - 1);
    }
    
    public void sendEvent(String tag,String param,JsonObject obj) {
    	sendEvent(new Serializable[] {tag,param,obj});
    }
    
    public void screenView(String screenName) {
    	JsonObject jo = new JreJsonFactory().createObject();
		jo.put("screen_name", screenName);
		////gtag('event', 'screen_view', {<screen_name>});
		sendEvent("event", "screen_view",jo);
    }
    
    protected void sendEventInternal(Serializable[] params) {
    	
    	LOGGER.info("sendEventInternal: {}",new Object[] {params});
    	
    	//gtag('config', '%1s',{'debug_mode':%2s});
    	//String json = jo.toJson();
    	
    	 ui.getPage().executeJavaScript("window.gtag.apply(null,arguments)",params);
    }
    
    protected void sendEvent(Serializable[] event) {
    	
    	pendingEvents.add(event);
    	sendEvents();
    	
    	//pendingEvents.add(createAction(command, fieldsObject, fields));
    	
    }
    
    SerializableConsumer<ExecutionContext> beforeResponse;
    
    protected void sendEvents() {
    	
    	LOGGER.info("sendEvents: {}",pendingEvents.size());
    	
    	if(beforeResponse == null && !pendingEvents.isEmpty()) {
    		beforeResponse = context -> {
    			  if(init()) {
                  	pendingEvents.forEach(this::sendEventInternal);
                  	pendingEvents.clear();
                  }
    			  beforeResponse = null;
    		};
    		ui.beforeClientResponse(ui,beforeResponse);
    	}

    }
    
    /*
     gtag('config', 'G-XXXXXXXX', {'user_id': 'USER_ID'});
    gtag('set', 'user_properties', { 'crm_id' : 'USER_ID' });
     */
    
    

	/* private void sendAction(Serializable[] action) {
		
		LOGGER.info("sendAction");
		
	
	    if (!pageViewPrefix.isEmpty()) {
	        // ["set", "page", location]
	        if (action.length == 3 && "set".equals(action[0]) && "page".equals(action[1])) {
	            action[2] = pageViewPrefix + action[2];
	        }
	    }
	
	    ui.getPage().executeJavaScript("ga.apply(null, arguments)", action);
	}
	*/
    private static Serializable[] createAction(String command, Map<String, ? extends Serializable> fieldsObject,
            Serializable... fields) {
        if (fields == null) {
            fields = new Serializable[] { null };
        }

        // [command, fields...]
        Stream<Serializable> argsStream = Stream.concat(Stream.of(command), Stream.of(fields));
        if (fieldsObject != null && !fieldsObject.isEmpty()) {
            // [command, fields..., fieldsObject]
            argsStream = Stream.concat(argsStream, Stream.of(toJsonObject(fieldsObject)));
        }

        return argsStream.toArray(Serializable[]::new);
    }

    private static JsonObject toJsonObject(Map<String, ? extends Serializable> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }

        return JsonUtils.createObject(map, JsonCodec::encodeWithoutTypeInfo);
    }

    /**
     * Sends a generic command to Google Analytics. This corresponds to a
     * client-side call to the <code>ga</code> function except that fieldsObject
     * is not the last parameter because of the way varargs work in Java.
     * 
     * @param command
     *            the name of the command to send, not <code>null</code>
     * @param fieldsObject
     *            a map of additional fields, or <code>null</code> to to not
     *            send any additional fields
     * @param fields
     *            a list of field values to send
     */
	/* public void ga(String command, Map<String, ? extends Serializable> fieldsObject, Serializable... fields) {
	    if (pendingActions.isEmpty()) {
	        ui.beforeClientResponse(ui, context -> {
	            if (!inited) {
	                init();
	            }
	
	            pendingActions.forEach(this::sendAction);
	            pendingActions.clear();
	        });
	    }
	
	    pendingActions.add(createAction(command, fieldsObject, fields));
	}*/

    
    
    public TrackerConfiguration getConfiguration() {
		return configuration;
	}

	/**
     * Sends a page view command to Google Analytics.
     * 
     * @param location
     *            the location of the viewed page, not <code>null</code>
     */
	/*public void sendPageView(String location) {
	    sendPageView(location, null);
	}*/

    /**
     * Sends a page view command with arbitrary additional fields to Google
     * Analytics. See <a href=
     * "https://developers.google.com/analytics/devguides/collection/analyticsjs/tracker-object-reference#send">the
     * reference documentation</a> for more information about supported
     * additional fields.
     * 
     * @param location
     *            the location of the viewed page, not <code>null</code>
     * @param fieldsObject
     *            map of additional fields to include in the <code>send</code>
     *            command
     */
	/* public void sendPageView(String location, Map<String, Serializable> fieldsObject) {
	    ga("set", null, "page", location);
	    ga("send", fieldsObject, "pageview");
	}*/

    /**
     * Sends an event command with the given category and action. See <a href=
     * "https://developers.google.com/analytics/devguides/collection/analyticsjs/tracker-object-reference#send">the
     * reference documentation</a> for information about the semantics of the
     * parameters.
     * 
     * @param category
     *            the category name, not <code>null</code>
     * @param action
     *            the action name, not <code>null</code>
     */
	/*  public void sendEvent(String category, String action) {
	    ga("send", null, "event", category, action);
	}
	*/
    /**
     * Sends an event command with the given category, action and label. See
     * <a href=
     * "https://developers.google.com/analytics/devguides/collection/analyticsjs/tracker-object-reference#send">the
     * reference documentation</a> for information about the semantics of the
     * parameters.
     * 
     * @param category
     *            the category name, not <code>null</code>
     * @param action
     *            the action name, not <code>null</code>
     * @param label
     *            the event label, not <code>null</code>
     */
	/*public void sendEvent(String category, String action, String label) {
	    ga("send", null, "event", category, action, label);
	}*/

    /**
     * Sends an event command with the given category, action, label and value.
     * See <a href=
     * "https://developers.google.com/analytics/devguides/collection/analyticsjs/tracker-object-reference#send">the
     * reference documentation</a> for information about the semantics of the
     * parameters.
     * 
     * @param category
     *            the category name, not <code>null</code>
     * @param action
     *            the action name, not <code>null</code>
     * @param label
     *            the event label, not <code>null</code>
     * @param value
     *            the event value
     */
	/*   public void sendEvent(String category, String action, String label, int value) {
	    ga("send", null, "event", category, action, label, Integer.valueOf(value));
	}*/

    /**
     * Sends an event command with the given category, action and arbitrary
     * additional fields. See <a href=
     * "https://developers.google.com/analytics/devguides/collection/analyticsjs/tracker-object-reference#send">the
     * reference documentation</a> for information about the semantics of the
     * parameters.
     * 
     * @param category
     *            the category name, not <code>null</code>
     * @param action
     *            the action name, not <code>null</code>
     * @param fieldsObject
     */
	/*  public void sendEvent(String category, String action, Map<String, Serializable> fieldsObject) {
	    ga("send", fieldsObject, "event", category, action);
	}*/

    /**
     * Checks whether this tracker has been initialized.
     * 
     * @return <code>true</code> if this tracker is initialized, otherwise
     *         <code>false</code>
     */
    public boolean isInitialized() {
        return inited;
    }
}
