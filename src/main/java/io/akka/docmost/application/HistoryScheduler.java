package io.akka.docmost.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import java.time.Duration;
import java.time.Instant;

/**
 * Schedules the close of a history window.
 *
 * <p>It reacts only to {@link PageEntity.WindowOpened}, which the entity emits solely on the
 * transition into an open window. Every later store inside that window emits nothing here, so the
 * timer name is never re-created while pending and the target's replace-on-same-name behaviour is
 * never reached.
 */
@Component(id = "history-scheduler")
@Consume.FromEventSourcedEntity(PageEntity.class)
public class HistoryScheduler extends Consumer {

  private final ComponentClient componentClient;

  public HistoryScheduler(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(PageEntity.Event event) {
    if (!(event instanceof PageEntity.WindowOpened opened)) {
      return effects().ignore();
    }
    var delay = Duration.between(Instant.now(), opened.closesAt());
    if (delay.isNegative()) {
      delay = Duration.ZERO;
    }
    timers()
        .createSingleTimer(
            timerName(opened.pageId()),
            delay,
            componentClient
                .forTimedAction()
                .method(HistoryCloser::close)
                .deferred(opened.pageId()));
    return effects().done();
  }

  static String timerName(String pageId) {
    return "history-window-" + pageId;
  }
}
