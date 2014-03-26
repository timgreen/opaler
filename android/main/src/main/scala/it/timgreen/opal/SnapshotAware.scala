package it.timgreen.opal

trait SnapshotAware {
  def preSnapshot() {}
  def postSnapshot() {}
}
