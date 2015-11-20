package it.timgreen.opal

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Fragment
import android.app.FragmentManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.support.v13.app.FragmentPagerAdapter
import android.support.v4.view.ViewPager
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

class TutorialActivity extends Activity {

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_tutorial)

    val tutorialPagerAdapter = new TutorialPagerAdapter(this, getFragmentManager)
    val viewPager = findViewById(R.id.tutorial_pager).asInstanceOf[ViewPager]
    viewPager.setAdapter(tutorialPagerAdapter)

    goFullscreen
  }

  private def goFullscreen() {
    // The UI options currently enabled are represented by a bitfield.
    // getSystemUiVisibility() gives us that bitfield.
    val uiOptions = getWindow.getDecorView.getSystemUiVisibility
    var newUiOptions = uiOptions

    // Navigation bar hiding:  Backwards compatible to ICS.
    if (Build.VERSION.SDK_INT >= 14) {
      newUiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }

    // Status bar hiding: Backwards compatible to Jellybean
    if (Build.VERSION.SDK_INT >= 16) {
      newUiOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN
    }

    // Immersive mode: Backward compatible to KitKat.
    // Note that this flag doesn't do anything by itself, it only augments the behavior
    // of HIDE_NAVIGATION and FLAG_FULLSCREEN.  For the purposes of this sample
    // all three flags are being toggled together.
    // Note that there are two immersive mode UI flags, one of which is referred to as "sticky".
    // Sticky immersive mode differs in that it makes the navigation and status bars
    // semi-transparent, and the UI flag does not get cleared when the user interacts with
    // the screen.
    if (Build.VERSION.SDK_INT >= 18) {
      newUiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    getWindow.getDecorView.setSystemUiVisibility(newUiOptions)
  }

  def startApp(view: View) {
    val intent = new Intent(this, classOf[MainActivity])
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    startActivity(intent)

    finish
  }
}

class TutorialPagerAdapter(context: Context, fm: FragmentManager) extends FragmentPagerAdapter(fm) {

  override def getCount = 5

  override def getItem(i: Int): Fragment = {
    val res = context.getResources
    val arrays = (
      res.obtainTypedArray(R.array.tutorial_titles),
      res.obtainTypedArray(R.array.tutorial_descriptions),
      res.obtainTypedArray(R.array.tutorial_images)
    )
    val (title, description, image) = (
      arrays._1.getString(i),
      arrays._2.getString(i),
      arrays._3.getDrawable(i)
    )
    arrays._1.recycle
    arrays._2.recycle
    arrays._3.recycle

    new TutorialFragment(
      i,
      getCount,
      title,
      description,
      image
    )
  }

  override def getPageTitle(position: Int): CharSequence = ""  // not in used
}

class TutorialFragment(i: Int, total: Int, title: String, description: String, image: Drawable) extends Fragment {

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup,
                            savedInstanceState: Bundle): View = {
    val rootView = inflater.inflate(R.layout.fragment_tutorial, container, false)

    rootView.findViewById(R.id.title).asInstanceOf[TextView].setText(title)
    rootView.findViewById(R.id.description).asInstanceOf[TextView].setText(description)
    rootView.findViewById(R.id.image).asInstanceOf[ImageView].setImageDrawable(image)

    val space = "&nbsp;"
    val unselected = space + "●"
    val select = space + "<font color='#FFFFFF'>●</font>"
    val indicator = Html.fromHtml(unselected * i + select + unselected * (total - 1 - i) + space)
    rootView.findViewById(R.id.indicator).asInstanceOf[TextView].setText(indicator)

    if (i + 1 < total) {
      rootView.findViewById(R.id.btn).setVisibility(View.GONE)
    } else {
      val start = Color.parseColor("#FFFFFF")
      val end = Color.parseColor("#999999")
      val va = ObjectAnimator.ofInt(rootView.findViewById(R.id.btn), "textColor", start, end)
      va.setDuration(450)
      va.setEvaluator(new ArgbEvaluator)
      va.setRepeatCount(ValueAnimator.INFINITE)
      va.setRepeatMode(ValueAnimator.REVERSE)
      va.start
    }

    rootView
  }
}
