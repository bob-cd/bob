# 7. Event Streaming

Date: 2023-05-31

## Status

Accepted

## Context

In order to build a state-of-the-art user interface we will need to stream updates to the user interface. The updates
should be giving the user enough context to understand what is currently going on in the CI. Therefore we need the
runners to send events to the apiserver which then sends these events out to clients.

Two choices need to be made here:

1. How to transport the events from the runners to the apiserver
2. How to distribute the events via apiserver to the clients

Critical requirements are:

- Each apiserver needs to get all the events (fan-out, non-destructive)
- Clients need to be allowed to connect to any running instance of apiservers (stateless)
- The current state of the CI and the history for a set amount of days need to be represented via events stream (replay)

## Decision

After going through some options we found the solution in a new feature of RabbitMQ named `Streams`. This enables us to
reuse existing software with only installing the stream plugin. It has the behaviour of known event-streaming platforms
and is easy to use. We only have to use the official java-streams-library and write some interop. There also is an
option for retention time of events so we can set the available history each client gets delivered on connection.

RabbitMQ Streams enables us to deliver events from the runners to the apiserver. To stream the events to the clients we
decided to use ring-server built-in feature to stream data as server-sent-events (SSE). This is technology that is based on
simple HTTP and widely understood by clients.

## Consequences

Change each runner to emit events via RabbitMQ stream. Implement SSE to stream events to connected clients on connect.
