package test;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import net.engio.mbassy.bus.MBassador;
import net.engio.mbassy.bus.publication.SyncAsyncPostCommand;
import net.engio.mbassy.listener.Handler;
import org.junit.Test;

import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Test to roughly compare speed difference between
 * async and synced eventbus with singlethreading
 */
public class EventBusBenchmark {

    private static final Logger LOGGER = Logger.getLogger(EventBusBenchmark.class.getName());

    @Test
    public void postEventsBenchmark() {
        EventBus asyncEventBus =  new AsyncEventBus(Executors.newFixedThreadPool(1));
        EventBus syncedEventBus = new EventBus();
        MBassador mBassador = new MBassador();

        int receiverCount = 1000;
        for(int i = 0; i < receiverCount; i++) {
            Receiver receiver = new Receiver();
            asyncEventBus.register(receiver);
            syncedEventBus.register(receiver);
            mBassador.subscribe(receiver);
        }


        int postCount = 10000;
        doPosts(asyncEventBus, postCount);
        doPosts(syncedEventBus, postCount);
        doPosts(mBassador, postCount, false);
        doPosts(mBassador, postCount, true);
    }
    private void doPosts(MBassador eventBus, int postCount, boolean async) {
        long start = System.currentTimeMillis();
        for(int i = 0; i < postCount; i++) {
            SyncAsyncPostCommand postCommand = eventBus.post(new Object());
            if(async) {
                postCommand.asynchronously();
            } else {
                postCommand.now();
            }
        }
        long durationInMs = System.currentTimeMillis() - start;
        LOGGER.info( eventBus.getClass().toString() + "(async " + async + ")" +
                " took " +
                durationInMs +
                " ms to complete " + postCount + " postings (" + (durationInMs / (float) postCount) + " per post)");
    }

    private void doPosts(EventBus eventBus, int postCount) {
        long start = System.currentTimeMillis();
        for(int i = 0; i < postCount; i++) {
            eventBus.post(new Object());
        }
        long durationInMs = System.currentTimeMillis() - start;
        LOGGER.info( eventBus.getClass().toString() +
                " took " +
                durationInMs +
                " ms to complete " + postCount + " postings (" + (durationInMs / (float) postCount) + " per post)");
    }

    private class Receiver {
        @Subscribe
        @Handler
        public void myMethod(Object object) {

        }
    }
}
