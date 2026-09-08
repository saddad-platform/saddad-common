package com.sadad.common.messaging;

public interface EventPublisher {
    <T> void publish(DomainEvent<T> event);
}
