package android.content

/**
 * Compile-time stub of the hidden framework interface `android.content.IContentProvider`.
 *
 * The original dex ships exactly this: an empty `public interface android.content.IContentProvider`
 * (R8 stripped all members). It only exists so that [com.jcapps.adbserver.FakeContentResolver] can
 * declare the hidden `ContentResolver.acquireProvider(...)` overrides with the right JVM signature.
 * At runtime the boot class path wins (parent-first class loading), so the real framework
 * interface is used and this stub is never loaded.
 */
interface IContentProvider
