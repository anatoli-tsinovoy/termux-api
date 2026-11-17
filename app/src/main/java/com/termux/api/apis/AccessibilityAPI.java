package com.termux.api.apis;

import android.content.Context;
import android.content.Intent;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import java.io.PrintWriter;

import com.termux.api.TermuxAccessibilityService;
import android.view.accessibility.AccessibilityNodeInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.io.StringWriter;

import android.graphics.Rect;

import android.provider.Settings;

import android.view.accessibility.AccessibilityManager;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.pm.ServiceInfo;
import java.util.List;
import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityWindowInfo;
import android.util.SparseArray;

import android.os.Bundle;

import android.view.Display;
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback;
import android.accessibilityservice.AccessibilityService.ScreenshotResult;
import android.hardware.HardwareBuffer;
import android.graphics.ColorSpace;
import android.graphics.Bitmap;
import java.io.OutputStream;
import java.io.IOException;
import java.lang.reflect.Field;

import android.hardware.display.DisplayManager;
import android.util.JsonWriter;
import com.termux.api.util.ResultReturner.ResultJsonWriter;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import android.graphics.Point;

import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import java.util.Locale;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class AccessibilityAPI {

    private static final String LOG_TAG = "AccessibilityAPI";

    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        // Not always reliable, see https://codeberg.org/Benjamin_Loison/Voice_assistant/issues/58#issuecomment-19684162
		/*boolean isAccessibilityEnabled = isAccessibilityServiceEnabled(context, TermuxAccessibilityService.class);
        if (!isAccessibilityEnabled) {
            Intent accessibilityIntent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            accessibilityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(accessibilityIntent);
        }*/

		int displayId = intent.getIntExtra("display-id", Display.DEFAULT_DISPLAY);

		if (intent.hasExtra("dump")) {
            TermuxAccessibilityService service = getEnabledAccessibilityService(context);
            if (service == null) {
                returnActionResult(apiReceiver, intent, false, "Accessibility service is disabled");
            } else {
                dump(apiReceiver, intent, displayId, service);
            }
		} else if (intent.hasExtra("click")) {
            dispatchGesture(apiReceiver, context, intent, false,
                    intent.hasExtra("x"), intent.getIntExtra("x", 0),
                    intent.hasExtra("y"), intent.getIntExtra("y", 0), 0, 0,
                    displayId, intent.getIntExtra("duration", 1));
        } else if (intent.hasExtra("swipe")) {
            dispatchGesture(apiReceiver, context, intent, true,
                    intent.hasExtra("x1"), intent.getIntExtra("x1", 0),
                    intent.hasExtra("y1"), intent.getIntExtra("y1", 0),
                    intent.hasExtra("x2") ? intent.getIntExtra("x2", 0) : 0,
                    intent.hasExtra("y2") ? intent.getIntExtra("y2", 0) : 0,
                    displayId, intent.getIntExtra("duration", 300));
		} else if (intent.hasExtra("type")) {
            TermuxAccessibilityService service = getEnabledAccessibilityService(context);
            if (service == null) {
                returnActionResult(apiReceiver, intent, false, "Accessibility service is disabled");
            } else {
                type(service, intent.getStringExtra("type"));
                returnEmptyString(apiReceiver, intent);
            }
		} else if (intent.hasExtra("global-action")) {
            TermuxAccessibilityService service = getEnabledAccessibilityService(context);
            if (service == null) {
                returnActionResult(apiReceiver, intent, false, "Accessibility service is disabled");
            } else {
                performGlobalAction(service, intent.getStringExtra("global-action"));
                returnEmptyString(apiReceiver, intent);
            }
		} else if (intent.hasExtra("screenshot")) {
            TermuxAccessibilityService service = getEnabledAccessibilityService(context);
            if (service == null) {
                returnActionResult(apiReceiver, intent, false, "Accessibility service is disabled");
            } else {
			    screenshot(apiReceiver, context, intent, displayId, service);
            }
		} else if (intent.hasExtra("list-displays")) {
			ResultReturner.returnData(apiReceiver, intent, new ResultJsonWriter() {
				@Override
				public void writeJson(final JsonWriter out) throws Exception {
					out.beginArray();
					DisplayManager displayManager = (DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);
					for(Display display : displayManager.getDisplays()) {
						out.beginObject();
						out.name("id").value(display.getDisplayId());
						out.name("name").value(display.getName());
						out.name("refresh_rate").value(display.getRefreshRate());
						out.name("flags").value(display.getFlags());
						Point size = new Point();
						display.getSize(size);
						out.name("height").value(size.y);
						out.name("width").value(size.x);
						out.endObject();
					}
					out.endArray();
				}
			});
        } else if (intent.hasExtra("get-string")) {
            ResultReturner.returnData(apiReceiver, intent, new ResultReturner.WithInput() {
                @Override
                public void writeResult(PrintWriter out) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.isEmpty()) {
                                String[] lineParts = line.split(" ");
                                String appId = lineParts[0];
                                String locale = lineParts[1];
                                String identifier = lineParts[2];
                                try {
                                    Resources res = context.getPackageManager().getResourcesForApplication(appId);

                                    Configuration cfg = new Configuration(res.getConfiguration());
                                    cfg.setLocale(new Locale(locale));

                                    res.updateConfiguration(cfg, res.getDisplayMetrics());

                                    int id = res.getIdentifier(identifier, "string", appId);
                                    String text = res.getString(id);

                                    out.println(text);
                                    out.flush();
                                } catch (PackageManager.NameNotFoundException e) {}
                            }
                        }
                    } catch(IOException e) {}
                }
            });
        }
    }

	// Necessary for void functions not to hang.
	private static void returnEmptyString(TermuxApiReceiver apiReceiver, Intent intent) {
		ResultReturner.returnData(apiReceiver, intent, out -> {});
	}

    // [The Stack Overflow answer 14923144](https://stackoverflow.com/a/14923144)
    public static boolean isAccessibilityServiceEnabled(Context context, Class<? extends AccessibilityService> service) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null)
            return false;
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

        for (AccessibilityServiceInfo enabledService : enabledServices) {
            ServiceInfo enabledServiceInfo = enabledService.getResolveInfo().serviceInfo;
            if (enabledServiceInfo.packageName.equals(context.getPackageName()) && enabledServiceInfo.name.equals(service.getName()))
                return true;
        }

        return false;
    }

    private static TermuxAccessibilityService getEnabledAccessibilityService(Context context) {
        if (!isAccessibilityServiceEnabled(context, TermuxAccessibilityService.class))
            return null;
        return TermuxAccessibilityService.instance;
    }

    private static void returnActionResult(TermuxApiReceiver apiReceiver, Intent intent, boolean success, String error) {
        ResultReturner.returnData(apiReceiver, intent, new ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                out.beginObject();
                out.name("success").value(success);
                if (error != null)
                    out.name("error").value(error);
                out.endObject();
            }
        });
    }

    private static String validateGesture(Context context, int x1, int y1, int x2, int y2,
                                         boolean swipe, int displayId, int duration) {
        if (duration < 1 || duration > 60000)
            return "Duration must be between 1 and 60000 milliseconds";
        if (x1 < 0 || y1 < 0 || (swipe && (x2 < 0 || y2 < 0)))
            return "Coordinates must not be negative";

        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        Display display = displayManager == null ? null : displayManager.getDisplay(displayId);
        if (display == null || !display.isValid())
            return "Invalid display id: " + displayId;

        Point displaySize = new Point();
        display.getRealSize(displaySize);
        if (x1 >= displaySize.x || y1 >= displaySize.y ||
                (swipe && (x2 >= displaySize.x || y2 >= displaySize.y)))
            return "Coordinates are outside the display bounds";
        return null;
    }

    private static void dispatchGesture(TermuxApiReceiver apiReceiver, Context context, Intent intent,
                                        boolean swipe, boolean hasX1, int x1, boolean hasY1, int y1,
                                        int x2, int y2, int displayId, int duration) {
        if (!hasX1 || !hasY1 || (swipe && (!intent.hasExtra("x2") || !intent.hasExtra("y2")))) {
            returnActionResult(apiReceiver, intent, false, "Missing gesture coordinates");
            return;
        }

        String validationError = validateGesture(context, x1, y1, x2, y2, swipe, displayId, duration);
        if (validationError != null) {
            returnActionResult(apiReceiver, intent, false, validationError);
            return;
        }
        if (getEnabledAccessibilityService(context) == null) {
            returnActionResult(apiReceiver, intent, false, "Accessibility service is disabled");
            return;
        }

        Path path = new Path();
        path.moveTo(x1, y1);
        if (swipe)
            path.lineTo(x2, y2);

        GestureDescription gesture;
        try {
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.setDisplayId(displayId);
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 0, duration));
            gesture = gestureBuilder.build();
        } catch (RuntimeException e) {
            returnActionResult(apiReceiver, intent, false, "Invalid gesture: " + e.getMessage());
            return;
        }

        GestureResponse response = new GestureResponse(duration + 5000L);
        Handler mainHandler = new Handler(Looper.getMainLooper());
        ResultReturner.returnData(apiReceiver, intent, new ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                response.writeJson(out);
            }
        });
        if (!mainHandler.post(() -> {
            TermuxAccessibilityService service = getEnabledAccessibilityService(context);
            if (service == null) {
                response.complete(false, "Accessibility service is disabled");
                return;
            }
            try {
                boolean accepted = service.dispatchGesture(gesture,
                        new AccessibilityService.GestureResultCallback() {
                            @Override
                            public void onCompleted(GestureDescription completedGesture) {
                                response.complete(true, null);
                            }

                            @Override
                            public void onCancelled(GestureDescription cancelledGesture) {
                                response.complete(false, "Gesture was cancelled");
                            }
                        }, mainHandler);
                if (!accepted)
                    response.complete(false, "Gesture dispatch was rejected");
            } catch (RuntimeException e) {
                String message = e.getMessage();
                response.complete(false, message == null ? "Gesture dispatch failed" :
                        "Gesture dispatch failed: " + message);
            }
        })) {
            response.complete(false, "Unable to dispatch gesture on the main thread");
        }
    }

    private static final class GestureResponse {
        private final CountDownLatch completed = new CountDownLatch(1);
        private boolean resolved;
        private final long timeoutMillis;
        private boolean success;
        private String error;

        private GestureResponse(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
        }
        private synchronized void complete(boolean success, String error) {
            if (!resolved) {
                resolved = true;
                this.success = success;
                this.error = error;
                completed.countDown();
            }
        }

        private void writeJson(JsonWriter out) throws IOException {
            try {
                if (!completed.await(timeoutMillis, TimeUnit.MILLISECONDS))
                    complete(false, "Gesture completion timed out");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                out.beginObject().name("success").value(false)
                        .name("error").value("Interrupted while waiting for gesture result").endObject();
                return;
            }
            out.beginObject().name("success").value(success);
            if (error != null)
                out.name("error").value(error);
            out.endObject();
        }
    }

    // The aim of this function is to give a compatible output with `adb` `uiautomator dump`.
    private static void dump(TermuxApiReceiver apiReceiver, Intent intent, int displayId,
                            TermuxAccessibilityService service) {
        SparseArray<List<AccessibilityWindowInfo>> windowsOnAllDisplays = service.getWindowsOnAllDisplays();
		List<AccessibilityWindowInfo> windowsOnDisplay = windowsOnAllDisplays.get(displayId);
		// https://codeberg.org/Benjamin_Loison/Voice_assistant/issues/114#issuecomment-22027078 shows such an occurrence
		if (windowsOnDisplay == null) {
			ResultReturner.returnData(apiReceiver, intent, out -> {});
            return;
        }

        int windowId = intent.getIntExtra("window-id", windowsOnDisplay.size() - 1);
        AccessibilityNodeInfo node = windowsOnDisplay.get(windowId).getRoot();
		// On Signal *App permissions* for instance
        if (node == null) {
			ResultReturner.returnData(apiReceiver, intent, out -> {});
            return;
        }

		String swString = dumpAuxiliary(node);

		ResultReturner.returnData(apiReceiver, intent, out -> {
			out.write(swString);
		});
    }

	private static String dumpAuxiliary(AccessibilityNodeInfo node) {
		// Create a DocumentBuilder
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = null;
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException parserConfigurationException) {
            Logger.logDebug(LOG_TAG, "ParserConfigurationException");
        }

        // Create a new Document
        Document document = builder.newDocument();

        // Create root element
        Element root = document.createElement("hierarchy");
        document.appendChild(root);

        dumpNodeAuxiliary(document, root, node);

        // Write as XML
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = null;
        try {
            transformer = transformerFactory.newTransformer();
        } catch (TransformerException transformerException) {
            Logger.logDebug(LOG_TAG, "TransformerException transformerFactory.newTransformer");
        }
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        // Necessary to not have surrogate pairs for emojis, see [Benjamin_Loison/Voice_assistant/issues/83#issue-3661619](https://codeberg.org/Benjamin_Loison/Voice_assistant/issues/83#issue-3661619)
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-16");
        DOMSource source = new DOMSource(document);

        StringWriter sw = new StringWriter();
        StreamResult result = new StreamResult(sw);
        try {
            transformer.transform(source, result);
        } catch (TransformerException transformerException) {
            Logger.logDebug(LOG_TAG, "TransformerException transformer.transform");
        }
		return sw.toString();
	}

    private static void dumpNodeAuxiliary(Document document, Element element, AccessibilityNodeInfo node) {
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo nodeChild = node.getChild(i);
            // May be faced randomly, see [Benjamin-Loison/android/issues/28#issuecomment-3975714760](https://github.com/Benjamin-Loison/android/issues/28#issuecomment-3975714760)
            if (nodeChild == null)
            {
                continue;
            }
            Element elementChild = document.createElement("node");

            elementChild.setAttribute("index", String.valueOf(i));

            elementChild.setAttribute("text", getCharSequenceAsString(nodeChild.getText()));

            String nodeChildViewIdResourceName = nodeChild.getViewIdResourceName();
            elementChild.setAttribute("resource-id", nodeChildViewIdResourceName != null ? nodeChildViewIdResourceName : "");

            elementChild.setAttribute("class", getCharSequenceAsString(nodeChild.getClassName()));

            elementChild.setAttribute("package", nodeChild.getPackageName().toString());

            elementChild.setAttribute("content-desc", getCharSequenceAsString(nodeChild.getContentDescription()));

            elementChild.setAttribute("checkable", String.valueOf(nodeChild.isCheckable()));

            elementChild.setAttribute("checked", String.valueOf(nodeChild.isChecked()));

            elementChild.setAttribute("clickable", String.valueOf(nodeChild.isClickable()));

            elementChild.setAttribute("enabled", String.valueOf(nodeChild.isEnabled()));

            elementChild.setAttribute("focusable", String.valueOf(nodeChild.isFocusable()));

            elementChild.setAttribute("focused", String.valueOf(nodeChild.isFocused()));

            elementChild.setAttribute("scrollable", String.valueOf(nodeChild.isScrollable()));

            elementChild.setAttribute("long-clickable", String.valueOf(nodeChild.isLongClickable()));

            elementChild.setAttribute("password", String.valueOf(nodeChild.isPassword()));

            elementChild.setAttribute("selected", String.valueOf(nodeChild.isSelected()));

            Rect nodeChildBounds = new Rect();
            nodeChild.getBoundsInScreen(nodeChildBounds);
            elementChild.setAttribute("bounds", nodeChildBounds.toShortString());

            elementChild.setAttribute("drawing-order", String.valueOf(nodeChild.getDrawingOrder()));

            elementChild.setAttribute("hint", getCharSequenceAsString(nodeChild.getHintText()));

            element.appendChild(elementChild);
            dumpNodeAuxiliary(document, elementChild, nodeChild);
        }
    }

    private static String getCharSequenceAsString(CharSequence charSequence) {
        return charSequence != null ? charSequence.toString() : "";
    }

    private static void type(TermuxAccessibilityService service, String toType) {
		SparseArray<List<AccessibilityWindowInfo>> windowsOnAllDisplays = service.getWindowsOnAllDisplays();
		for(int windowsOnAllDisplayIndex = 0; windowsOnAllDisplayIndex < windowsOnAllDisplays.size(); windowsOnAllDisplayIndex++)
        {
            List<AccessibilityWindowInfo> windowsOnAllDisplay = windowsOnAllDisplays.valueAt(windowsOnAllDisplayIndex);
            for(AccessibilityWindowInfo accessibilityWindowInfo : windowsOnAllDisplay)
			{
				AccessibilityNodeInfo focusedNode = accessibilityWindowInfo.getRoot().findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
				if(focusedNode != null)
				{
					Bundle arguments = new Bundle();
					arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, toType);
					focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
					return;
				}
			}
		}
    }

    private static void performGlobalAction(TermuxAccessibilityService service, String globalActionString) {
		String fieldName = "GLOBAL_ACTION_" + globalActionString.toUpperCase();
		Field field = null;
		try {
			field = AccessibilityService.class.getDeclaredField(fieldName);
		} catch (NoSuchFieldException noSuchFieldException) {
			Logger.logDebug(LOG_TAG, "NoSuchFieldException");
		}
		Object globalActionObject = null;
		try {
			globalActionObject = field.get(null);
		} catch(IllegalAccessException illegalAccessException) {
			Logger.logDebug(LOG_TAG, "IllegalAccessException");
		}
		int globalActionInt = (int)globalActionObject;
        service.performGlobalAction(globalActionInt);
    }

	private static void screenshot(TermuxApiReceiver apiReceiver, final Context context, Intent intent,
                                   int displayId, TermuxAccessibilityService service) {
		service.takeScreenshot(
            displayId,
            context.getMainExecutor(),
            new TakeScreenshotCallback() {

                @Override
                public void onSuccess(ScreenshotResult screenshotResult) {
                    Logger.logDebug(LOG_TAG, "onSuccess");
                    HardwareBuffer buffer = screenshotResult.getHardwareBuffer();
                    ColorSpace colorSpace = screenshotResult.getColorSpace();

                    Bitmap bitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace);

                    ResultReturner.returnData(apiReceiver, intent, new ResultReturner.BinaryOutput()
                    {
                        @Override
                        public void writeResult(OutputStream out) throws IOException {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                        }
                    });
                }

                @Override
                public void onFailure(int errorCode) {
                    Logger.logDebug(LOG_TAG, "onFailure: " + errorCode);
                }
            }
        );
	}
}
