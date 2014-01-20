package com.jakewharton.scalpel;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import static android.graphics.Paint.ANTI_ALIAS_FLAG;
import static android.graphics.Paint.Style.STROKE;
import static android.graphics.Typeface.NORMAL;
import static android.os.Build.VERSION_CODES.JELLY_BEAN;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.INVALID_POINTER_ID;

/**
 * Renders your view hierarchy as an interactive 3D visualization of layers.
 * <p>
 * Interactions supported:
 * <ul>
 * <li>Single touch: controls the rotation of the model.</li>
 * <li>Two finger vertical pinch: Adjust zoom.</li>
 * <li>Two finger horizontal pinch: Adjust layer spacing.</li>
 * </ul>
 */
public class ScalpelFrameLayout extends FrameLayout {
  private static final int TRACKING_UNKNOWN = 0;
  private static final int TRACKING_VERTICALLY = 1;
  private static final int TRACKING_HORIZONTALLY = -1;
  private static final int ROTATION_MAX = 60;
  private static final int ROTATION_MIN = -ROTATION_MAX;
  private static final int ROTATION_DEFAULT_X = -10;
  private static final int ROTATION_DEFAULT_Y = 15;
  private static final float ZOOM_DEFAULT = 0.6f;
  private static final float ZOOM_MIN = 0.33f;
  private static final float ZOOM_MAX = 2f;
  private static final int SPACING_DEFAULT = 25;
  private static final int SPACING_MIN = 10;
  private static final int SPACING_MAX = 100;
  private static final int CHROME_COLOR = 0xFF888888;
  private static final int CHROME_SHADOW_COLOR = 0xFF000000;
  private static final int TEXT_OFFSET_DP = 2;
  private static final int TEXT_SIZE_DP = 10;
  private static final boolean DEBUG = false;

  private static void log(String message, Object... args) {
    Log.d("Scalpel", String.format(message, args));
  }

  private final Rect viewBoundsRect = new Rect();
  private final Paint viewBorderPaint = new Paint(ANTI_ALIAS_FLAG);
  private final Camera camera = new Camera();
  private final Matrix matrix = new Matrix();
  private final int[] location = new int[2];
  private final SparseArray<String> idNames = new SparseArray<>();
  private final Deque<ViewNode> viewNodeQueue = new ArrayDeque<>();

  private final Resources res;
  private final float density;
  private final float slop;
  private final float textOffset;
  private final float textSize;

  private boolean enabled;
  private boolean drawViews = true;
  private boolean drawIds;

  private int pointerOne = INVALID_POINTER_ID;
  private float lastOneX;
  private float lastOneY;
  private int pointerTwo = INVALID_POINTER_ID;
  private float lastTwoX;
  private float lastTwoY;
  private int multiTouchTracking = TRACKING_UNKNOWN;

  private float rotationY = ROTATION_DEFAULT_Y;
  private float rotationX = ROTATION_DEFAULT_X;
  private float zoom = ZOOM_DEFAULT;
  private float spacing = SPACING_DEFAULT;

  private ViewNode root;

  public ScalpelFrameLayout(Context context) {
    this(context, null);
  }

  public ScalpelFrameLayout(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ScalpelFrameLayout(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    res = context.getResources();
    density = context.getResources().getDisplayMetrics().density;
    slop = ViewConfiguration.get(context).getScaledTouchSlop();

    textSize = TEXT_SIZE_DP * density;
    textOffset = TEXT_OFFSET_DP * density;

    viewBorderPaint.setColor(CHROME_COLOR);
    viewBorderPaint.setStyle(STROKE);
    viewBorderPaint.setTextSize(textSize);
    viewBorderPaint.setShadowLayer(1, -1, 1, CHROME_SHADOW_COLOR);
    if (Build.VERSION.SDK_INT >= JELLY_BEAN) {
      viewBorderPaint.setTypeface(Typeface.create("sans-serif-condensed", NORMAL));
    }
  }

  /** Set whether or not the 3D view layer interaction is enabled. */
  public void setLayerInteractionEnabled(boolean enabled) {
    if (this.enabled != enabled) {
      this.enabled = enabled;
      setWillNotDraw(!enabled);
      invalidate();

      if (enabled) {
        root = new ViewNode(this, -1);
      } else {
        root.restoreVisibility();
        root = null;
      }
    }
  }

  /** Returns true when 3D view layer interaction is enabled. */
  public boolean isLayerInteractionEnabled() {
    return enabled;
  }

  /** Set whether the view layers draw their contents. When false, only wireframes are shown. */
  public void setDrawViews(boolean drawViews) {
    if (this.drawViews != drawViews) {
      this.drawViews = drawViews;
      invalidate();
    }
  }

  /** Returns true when view layers draw their contents. */
  public boolean isDrawingViews() {
    return drawViews;
  }

  /** Set whether the view layers draw their IDs. */
  public void setDrawIds(boolean drawIds) {
    if (this.drawIds != drawIds) {
      this.drawIds = drawIds;
      invalidate();
    }
  }

  /** Returns true when view layers draw their IDs. */
  public boolean isDrawingIds() {
    return drawIds;
  }

  @Override public boolean onInterceptTouchEvent(MotionEvent ev) {
    return enabled || super.onInterceptTouchEvent(ev);
  }

  @Override public boolean onTouchEvent(@SuppressWarnings("NullableProblems") MotionEvent event) {
    if (!enabled) {
      return super.onTouchEvent(event);
    }

    int action = event.getActionMasked();
    switch (action) {
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_POINTER_DOWN: {
        int index = (action == ACTION_DOWN) ? 0 : event.getActionIndex();
        if (pointerOne == INVALID_POINTER_ID) {
          pointerOne = event.getPointerId(index);
          lastOneX = event.getX(index);
          lastOneY = event.getY(index);
          if (DEBUG) log("Got pointer 1!  id: %s  x: %s  y: %s", pointerOne, lastOneY, lastOneY);
        } else if (pointerTwo == INVALID_POINTER_ID) {
          pointerTwo = event.getPointerId(index);
          lastTwoX = event.getX(index);
          lastTwoY = event.getY(index);
          if (DEBUG) log("Got pointer 2!  id: %s  x: %s  y: %s", pointerTwo, lastTwoY, lastTwoY);
        } else {
          if (DEBUG) log("Ignoring additional pointer.  id: %s", event.getPointerId(index));
        }
        break;
      }

      case MotionEvent.ACTION_MOVE: {
        if (pointerTwo == INVALID_POINTER_ID) {
          // Single pointer controlling 3D rotation.
          for (int i = 0, count = event.getPointerCount(); i < count; i++) {
            if (pointerOne == event.getPointerId(i)) {
              float eventX = event.getX(i);
              float eventY = event.getY(i);
              float dx = eventX - lastOneX;
              float dy = eventY - lastOneY;
              float drx = 90 * (dx / getWidth());
              float dry = 90 * (-dy / getHeight()); // Invert Y-axis.
              // An 'x' delta affects 'y' rotation and vise versa.
              rotationY = Math.min(Math.max(rotationY + drx, ROTATION_MIN), ROTATION_MAX);
              rotationX = Math.min(Math.max(rotationX + dry, ROTATION_MIN), ROTATION_MAX);
              if (DEBUG) {
                log("Single pointer moved (%s, %s) affecting rotation (%s, %s).", dx, dy, drx, dry);
              }

              lastOneX = eventX;
              lastOneY = eventY;

              invalidate();
            }
          }
        } else {
          // We know there's two pointers and we only care about pointerOne and pointerTwo
          int pointerOneIndex = event.findPointerIndex(pointerOne);
          int pointerTwoIndex = event.findPointerIndex(pointerTwo);

          float xOne = event.getX(pointerOneIndex);
          float yOne = event.getY(pointerOneIndex);
          float xTwo = event.getX(pointerTwoIndex);
          float yTwo = event.getY(pointerTwoIndex);

          float dxOne = xOne - lastOneX;
          float dyOne = yOne - lastOneY;
          float dxTwo = xTwo - lastTwoX;
          float dyTwo = yTwo - lastTwoY;

          if (multiTouchTracking == TRACKING_UNKNOWN) {
            float adx = Math.abs(dxOne) + Math.abs(dxTwo);
            float ady = Math.abs(dyOne) + Math.abs(dyTwo);

            if (adx > slop * 2 || ady > slop * 2) {
              if (adx > ady) {
                // Left/right movement wins. Track horizontal.
                multiTouchTracking = TRACKING_HORIZONTALLY;
              } else {
                // Up/down movement wins. Track vertical.
                multiTouchTracking = TRACKING_VERTICALLY;
              }
            }
          }

          if (multiTouchTracking == TRACKING_VERTICALLY) {
            if (yOne >= yTwo) {
              zoom += dyOne / getHeight() - dyTwo / getHeight();
            } else {
              zoom += dyTwo / getHeight() - dyOne / getHeight();
            }

            zoom = Math.min(Math.max(zoom, ZOOM_MIN), ZOOM_MAX);
            invalidate();
          } else if (multiTouchTracking == TRACKING_HORIZONTALLY) {
            if (xOne >= xTwo) {
              spacing += (dxOne / getWidth() * SPACING_MAX) - (dxTwo / getWidth() * SPACING_MAX);
            } else {
              spacing += (dxTwo / getWidth() * SPACING_MAX) - (dxOne / getWidth() * SPACING_MAX);
            }

            spacing = Math.min(Math.max(spacing, SPACING_MIN), SPACING_MAX);
            invalidate();
          }

          if (multiTouchTracking != TRACKING_UNKNOWN) {
            lastOneX = xOne;
            lastOneY = yOne;
            lastTwoX = xTwo;
            lastTwoY = yTwo;
          }
        }
        break;
      }

      case MotionEvent.ACTION_CANCEL:
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_POINTER_UP: {
        int index = (action != ACTION_POINTER_UP) ? 0 : event.getActionIndex();
        int pointerId = event.getPointerId(index);
        if (pointerOne == pointerId) {
          // Shift pointer two (real or invalid) up to pointer one.
          pointerOne = pointerTwo;
          lastOneX = lastTwoX;
          lastOneY = lastTwoY;
          if (DEBUG) log("Promoting pointer 2 (%s) to pointer 1.", pointerTwo);
          // Clear pointer two and tracking.
          pointerTwo = INVALID_POINTER_ID;
          multiTouchTracking = TRACKING_UNKNOWN;
        } else if (pointerTwo == pointerId) {
          if (DEBUG) log("Lost pointer 2 (%s).", pointerTwo);
          pointerTwo = INVALID_POINTER_ID;
          multiTouchTracking = TRACKING_UNKNOWN;
        }
        break;
      }
    }

    return true;
  }

  @Override public void draw(@SuppressWarnings("NullableProblems") Canvas canvas) {
    if (!enabled) {
      super.draw(canvas);
      return;
    }

    getLocationInWindow(location);
    float x = location[0];
    float y = location[1];

    int saveCount = canvas.save();

    float cx = getWidth() / 2f;
    float cy = getHeight() / 2f;

    camera.save();
    camera.rotate(rotationX, rotationY, 0);
    camera.getMatrix(matrix);
    camera.restore();

    matrix.preTranslate(-cx, -cy);
    matrix.postTranslate(cx, cy);
    canvas.concat(matrix);
    canvas.scale(zoom, zoom, cx, cy);

    // We don't want to be rendered so seed the queue with our children.
    viewNodeQueue.addAll(root.children);

    while (!viewNodeQueue.isEmpty()) {
      ViewNode viewNode = viewNodeQueue.removeFirst();
      View view = viewNode.view;
      int layer = viewNode.level;

      int viewSaveCount = canvas.save();

      // Scale the layer index translation by the rotation amount.
      float translateShowX = rotationY / ROTATION_MAX;
      float translateShowY = rotationX / ROTATION_MAX;
      float tx = layer * spacing * density * translateShowX;
      float ty = layer * spacing * density * translateShowY;
      canvas.translate(tx, -ty);

      view.getLocationInWindow(location);
      canvas.translate(location[0] - x, location[1] - y);

      viewBoundsRect.set(0, 0, view.getWidth(), view.getHeight());
      canvas.drawRect(viewBoundsRect, viewBorderPaint);

      if (drawViews) {
        view.draw(canvas);
      }

      if (drawIds) {
        int id = view.getId();
        if (id != NO_ID) {
          canvas.drawText(nameForId(id), textOffset, textSize, viewBorderPaint);
        }
      }

      canvas.restoreToCount(viewSaveCount);

      // Queue any children for later drawing.
      if (viewNode.hasChildren) {
        for (ViewNode childNode : viewNode.children) {
          viewNodeQueue.add(childNode);
        }
      }
    }

    canvas.restoreToCount(saveCount);
  }

  private String nameForId(int id) {
    String name = idNames.get(id);
    if (name == null) {
      try {
        name = res.getResourceEntryName(id);
      } catch (NotFoundException e) {
        name = String.format("0x%8x", id);
      }
      idNames.put(id, name);
    }
    return name;
  }

  private static class ViewNode {
    private static final Rect CHILD_RECT = new Rect();
    private static final Rect TEST_RECT = new Rect();

    private final View view;
    private final int level;
    private final int layers;
    private final boolean hasChildren;
    private final List<ViewNode> children;

    private ViewNode(View view, int level) {
      this.view = view;
      this.level = level;

      if (view instanceof ViewGroup) {
        ViewGroup viewGroup = (ViewGroup) view;
        int childCount = viewGroup.getChildCount();
        hasChildren = childCount > 0;
        children = new ArrayList<>(childCount);
        layers = noderizeChildren(viewGroup, level, children);
      } else {
        hasChildren = false;
        children = Collections.emptyList();
        layers = 0;
      }
    }

    private void restoreVisibility() {
      view.setVisibility(VISIBLE);
      for (ViewNode child : children) {
        child.restoreVisibility();
      }
    }

    private static int noderizeChildren(ViewGroup viewGroup, int level, List<ViewNode> children) {
      int layers = 0;
      for (int i = 0, count = viewGroup.getChildCount(); i < count; i++) {
        View child = viewGroup.getChildAt(i);

        //noinspection ConstantConditions
        if (child.getVisibility() != VISIBLE) {
          continue;
        }

        child.getHitRect(CHILD_RECT);

        // Detect draw overlap and position above any overlapping views.
        int childLevel = level + 1;
        for (ViewNode childNode : children) {
          childNode.view.getHitRect(TEST_RECT);
          if (TEST_RECT.intersect(CHILD_RECT)) {
            if (DEBUG) log("Intersection: %s and %s", TEST_RECT, CHILD_RECT);
            int childTopLevel = childNode.level + childNode.layers;
            if (childTopLevel >= childLevel) {
              childLevel = childTopLevel + 1;
            }
          }
        }

        ViewNode childNode = new ViewNode(child, childLevel);
        int childLayers = childNode.layers + 1;
        if (childLayers > layers) {
          layers = childLayers;
        }
        children.add(childNode);

        // Mark child invisible since we draw every view group with its children absent. We invoke
        // draw directly on children thus bypassing the need for this view to even be visible.
        child.setVisibility(INVISIBLE);
      }
      return layers;
    }
  }
}
