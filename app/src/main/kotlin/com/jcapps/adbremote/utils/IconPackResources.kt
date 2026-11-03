package com.jcapps.adbremote.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException

/**
 * A third-party icon pack: its resources, `appfilter.xml` mapping and a drawable-id cache.
 *
 * RECON-UNSURE: [create], [loadIcon], [load] and [getAppFilterParser] were all inlined by R8 (only
 * their `"context"`/`"packageName"` parameter-check leftovers remain); their names are guesses.
 */
class IconPackResources(
    // obf: f4637
    val packageName: String,
) {
    // obf: C0723

    // obf: f4636
    lateinit var packResources: Resources

    // obf: f4639
    lateinit var label: String

    /** app package name -> drawable name (from appfilter.xml). RECON-UNSURE: name */
    // obf: f4638
    val iconMap = LinkedHashMap<String, String>()

    /** drawable name -> resolved resource id cache. RECON-UNSURE: name */
    // obf: f4640
    val drawableIds = LinkedHashMap<String, Int>()

    /** Icon of the icon-pack app itself. */
    fun loadIcon(context: Context): Drawable {
        val packageManager = context.packageManager
        return packageManager.getApplicationInfo(packageName, 0).let { it.loadIcon(packageManager) }
    }

    /** Loads [packResources] and parses `appfilter.xml` into [iconMap]. */
    fun load(context: Context) {
        packResources = context.packageManager.getResourcesForApplication(packageName)
        val parser = getAppFilterParser(context) ?: return
        val prefix = "ComponentInfo{"
        val prefixLength = prefix.length
        val suffix = "}"
        val suffixLength = suffix.length
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            try {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "item") {
                    var component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (component != null && drawable != null) {
                        if (component.startsWith(prefix) && component.endsWith(suffix)) {
                            component = component.substring(prefixLength, component.length - suffixLength)
                        }
                        val componentName = ComponentName.unflattenFromString(component)
                        if (componentName != null) {
                            iconMap[componentName.packageName] = drawable
                        }
                    }
                }
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            } catch (e: XmlPullParserException) {
                e.printStackTrace()
            }
        }
    }

    private fun getAppFilterParser(context: Context): XmlPullParser? {
        val name = "appfilter"
        return try {
            val resources = context.packageManager.getResourcesForApplication(packageName)
            val identifier = resources.getIdentifier(name, "xml", packageName)
            if (identifier != 0) {
                context.packageManager.getXml(packageName, identifier, null)
            } else {
                XmlPullParserFactory.newInstance().newPullParser().apply {
                    setInput(resources.assets.open("$name.xml"), Xml.Encoding.UTF_8.toString())
                }
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: IOException) {
            null
        } catch (_: XmlPullParserException) {
            null
        }
    }

    companion object {
        /** Null when [packageName] is not installed. */
        fun create(context: Context, packageName: String): IconPackResources? = try {
            IconPackResources(packageName).apply {
                val packageManager = context.packageManager
                label = packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}
