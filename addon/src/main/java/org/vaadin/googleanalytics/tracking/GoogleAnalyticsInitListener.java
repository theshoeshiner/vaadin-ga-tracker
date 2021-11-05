package org.vaadin.googleanalytics.tracking;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;

/**
 * Automatically registers a navigation listener that sends page views to Google
 * Analytics.
 */
public class GoogleAnalyticsInitListener implements VaadinServiceInitListener {
	
	public static final Logger LOGGER = LoggerFactory.getLogger(GoogleAnalyticsInitListener.class);
	
    @Override
    public void serviceInit(ServiceInitEvent event) {
    	
    	LOGGER.info("service init");
    	
    	//dont need to send page views any more
		event.getSource().addUIInitListener(uiInit -> {
		    UI ui = uiInit.getUI();
		    //GoogleAnalyticsTracker tracker = GoogleAnalyticsTracker.get(ui);
		    //tracker.init();
		
			 ui.addAfterNavigationListener(navigationEvent -> {
				 LOGGER.info("navigation event");
			    GoogleAnalyticsTracker tracker = GoogleAnalyticsTracker.get(ui);
			    tracker.init();
				if (shouldTrack(tracker, navigationEvent)) {
					LOGGER.info("should track");
				    //tracker.sendPageView(navigationEvent.getLocation().getPathWithQueryParameters());
					//gtag('event', 'screen_view', {<screen_name>});
					//JsonOb
					//tracker.sendEvent("event", "screen_view",);
					//tracker.screenView(navigationEvent);
					screenName(navigationEvent).ifPresent(sn -> {
						LOGGER.info("screenname: {}",sn);
						tracker.screenView(sn);
					});
										
				}
			});
		});
    }

    private static boolean shouldTrack(GoogleAnalyticsTracker tracker, AfterNavigationEvent navigationEvent) {
        if (hasIgnore(navigationEvent)) {
        	LOGGER.info("has ignore");
            return false;
        }

        LOGGER.info("init: {} caninit: {}",tracker.isInitialized(),canInitialize(navigationEvent));
        /*
         * Track if tracker is already initialized or if it can be initialized
         * based on the current navigation event.
         */
        return tracker.isInitialized() || canInitialize(navigationEvent);
    }

    private static boolean canInitialize(AfterNavigationEvent navigationEvent) {
        List<HasElement> routerChain = navigationEvent.getActiveChain();
        if (routerChain.isEmpty()) {
            return false;
        }

        Class<? extends HasElement> rootLayoutClass = getRootLayout(routerChain);

        return rootLayoutClass.getAnnotation(EnableGoogleAnalytics.class) != null
                || TrackerConfigurator.class.isAssignableFrom(rootLayoutClass);
    }

    private static Class<? extends HasElement> getRootLayout(List<HasElement> routerChain) {
        return routerChain.get(routerChain.size() - 1).getClass();
    }
    
    private static Optional<String> screenName(AfterNavigationEvent navigationEvent) {
        return navigationEvent.getActiveChain().stream().filter(he -> {
        	LOGGER.info("filter: {} = {}",he,he.getClass().getAnnotation(ScreenName.class));
        	
        	return he.getClass().getAnnotation(ScreenName.class) != null;
        }).map(he -> {
        	LOGGER.info("map: {}",he.getClass().getAnnotation(ScreenName.class).value());
        	return he.getClass().getAnnotation(ScreenName.class).value();
        }).findFirst();
    }

    private static boolean hasIgnore(AfterNavigationEvent navigationEvent) {
        return navigationEvent.getActiveChain().stream().anyMatch(GoogleAnalyticsInitListener::hasIgnoreAnnotation);
    }

    private static boolean hasIgnoreAnnotation(HasElement target) {
        return target.getClass().getAnnotation(IgnorePageView.class) != null;
    }
}
