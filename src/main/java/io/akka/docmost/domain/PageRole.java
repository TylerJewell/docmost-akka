package io.akka.docmost.domain;

/** A role granted by a page-level restriction. WRITER outranks READER (R4). */
public enum PageRole {
  READER,
  WRITER
}
