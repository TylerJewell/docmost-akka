package io.akka.docmost.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timedaction.TimedAction;

/** Closes a page's history window when its timer fires. */
@Component(id = "history-closer")
public class HistoryCloser extends TimedAction {

  private final ComponentClient componentClient;

  public HistoryCloser(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect close(String pageId) {
    componentClient.forEventSourcedEntity(pageId).method(PageEntity::closeWindow).invoke();
    return effects().done();
  }
}
