package it.timgreen.opalwear

import android.app.Activity
import android.os.Bundle
import android.support.wearable.view.WatchViewStub
import android.widget.TextView

class WearActivity extends Activity {

  var textView: Option[TextView] = _

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_wear)
    val stub = findViewById(R.id.watch_view_stub).asInstanceOf[WatchViewStub]
    stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
      override def onLayoutInflated(stub: WatchViewStub) {
        textView = Some(stub.findViewById(R.id.text).asInstanceOf[TextView])
      }
    })
  }

  override def onStart() {
    super.onStart
    textView foreach { _.setText("Opaler Wear") }
  }
}
