package it.timgreen.android;

import java.util.ArrayList;
import java.util.List;

/**
 * From: https://www.assembla.com/code/scala-eclipse-toolchain/git/nodes/ad17dd4047ac25b167496db84f4272795eb93c3e/docs/android-examples/android-sdk/Wiktionary/src/com/example/android/wiktionary/MyAsyncTask.java
 *
 * Temporary workaround to solve a Scala compiler issue which shows up
 * at runtime with the error message
 * "java.lang.AbstractMethodError: abstract method not implemented"
 * for the missing method LookupTask.doInBackground(String... args).
 *
 * Our solution: the Java method doInBackground(String... args) forwards
 * the call to the Scala method doInBackground1(String[] args).
 */
public abstract class AsyncTask<Params, Progress, Result>
    extends android.os.AsyncTask<Params, Progress, Result> {

  protected abstract Result doInBackgroundWithArray(List<Params> args);

  @Override
  protected final Result doInBackground(Params... args) {
    List<Params> argsList = new ArrayList<Params>();
    for (int i = 0; i < args.length; i++) {
      argsList.add(args[i]);
    }
    return doInBackgroundWithArray(argsList);
  }
}
