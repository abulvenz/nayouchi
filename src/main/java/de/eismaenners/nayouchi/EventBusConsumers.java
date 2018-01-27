/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.eismaenners.nayouchi;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.starter.MainVerticle;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author iceman
 */
public class EventBusConsumers {

    public static void setupConsumers(Object self, final EventBus eventBus) throws SecurityException {
        Arrays.stream(self.getClass().getMethods())
                .filter(method -> method.isAnnotationPresent(Consumes.class))
                .forEach(method -> addConsumerMethod(self, method, eventBus));
    }

    private static MessageConsumer<Object> addConsumerMethod(Object self, Method method, final EventBus eventBus) {
        return eventBus.consumer(method.getAnnotation(Consumes.class).path(), secureExecution(self, method));
    }

    private static Handler<Message<Object>> secureExecution(Object self, Method method) {
        return (arg) -> {
            try {
                method.invoke(self, new Object[]{arg});
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                Logger.getLogger(MainVerticle.class.getName()).log(Level.SEVERE, null, ex);
            }
        };
    }

}
