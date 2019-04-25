package com.bluelinelabs.conductor;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.bluelinelabs.conductor.internal.ViewAttachHandler;
import com.bluelinelabs.conductor.internal.ViewAttachHandler.ViewAttachListener;
import com.bluelinelabs.conductor.util.ActivityProxy;
import com.bluelinelabs.conductor.util.ViewUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ViewAttachHandlerTests {

    private Activity activity;
    private ViewAttachHandler viewAttachHandler;
    private CountingViewAttachListener viewAttachListener;

    @Before
    public void setup() {
        activity = new ActivityProxy().create(null).getActivity();
        viewAttachListener = new CountingViewAttachListener();
        viewAttachHandler = new ViewAttachHandler(viewAttachListener);
    }

    @Test
    public void testSimpleViewAttachDetach() {
        View view = new View(activity);
        viewAttachHandler.listenForAttach(view);

        assertEquals(0, viewAttachListener.attaches);
        assertEquals(0, viewAttachListener.detaches);

        ViewUtils.reportAttached(view, true);
        assertEquals(1, viewAttachListener.attaches);
        assertEquals(0, viewAttachListener.detaches);

        ViewUtils.reportAttached(view, true);
        assertEquals(1, viewAttachListener.attaches);
        assertEquals(0, viewAttachListener.detaches);

        ViewUtils.reportAttached(view, false);
        assertEquals(1, viewAttachListener.attaches);
        assertEquals(1, viewAttachListener.detaches);

        ViewUtils.reportAttached(view, false);
        assertEquals(1, viewAttachListener.attaches);
        assertEquals(1, viewAttachListener.detaches);

        ViewUtils.reportAttached(view, true);
        assertEquals(2, viewAttachListener.attaches);
        assertEquals(1, viewAttachListener.detaches);
    }

    @Test
    public void testSimpleViewGroupAttachDetach() {
        View view = new LinearLayout(activity);
        viewAttachHandler.listenForAttach(view);

        assertEquals(0, viewAttachListener.attaches);
        assertEquals(0, viewAttachListener.detaches);

        ViewUtils.reportAttached(view, true);
        assertEquals(1, viewAttachListener.attaches);
        assertEquals(0, viewAttachListener.detaches);

        ViewUtils.reportAttached(view, true);
        assertEquals(1, viewAttachListener.attaches);
        assertEquals(0, viewAttachListener.detaches);

        ViewUtils.reportAttached(view, false);
        assertEquals(1, viewAttachListener.attaches);
        assertEquals(1, viewAttachListener.detaches);

        ViewUtils.reportAttached(view, false);
        assertEquals(1, viewAttachListener.attaches);
        assertEquals(1, viewAttachListener.detaches);

        ViewUtils.reportAttached(view, true);
        assertEquals(2, viewAttachListener.attaches);
        assertEquals(1, viewAttachListener.detaches);
    }

    @Test
    public void testNestedViewGroupAttachDetach() {
        ViewGroup view = new LinearLayout(activity);
        View child = new LinearLayout(activity);
        view.addView(child);
        viewAttachHandler.listenForAttach(view);

        assertEquals(0, viewAttachListener.attaches);
        assertEquals(0, viewAttachListener.detaches);

        ViewUtils.reportAttached(view, true, false);
        assertEquals(0, viewAttachListener.attaches);
        assertEquals(0, viewAttachListener.detaches);

        ViewUtils.reportAttached(child, true, false);
        assertEquals(1, viewAttachListener.attaches);
        assertEquals(0, viewAttachListener.detaches);

        ViewUtils.reportAttached(view, true, false);
        ViewUtils.reportAttached(child, true, false);
        assertEquals(1, viewAttachListener.attaches);
        assertEquals(0, viewAttachListener.detaches);

        ViewUtils.reportAttached(view, false, false);
        assertEquals(1, viewAttachListener.attaches);
        assertEquals(1, viewAttachListener.detaches);

        ViewUtils.reportAttached(view, false, false);
        assertEquals(1, viewAttachListener.attaches);
        assertEquals(1, viewAttachListener.detaches);

        ViewUtils.reportAttached(view, true, false);
        assertEquals(1, viewAttachListener.attaches);
        assertEquals(1, viewAttachListener.detaches);

        ViewUtils.reportAttached(child, true, false);
        assertEquals(2, viewAttachListener.attaches);
        assertEquals(1, viewAttachListener.detaches);
    }

    private static class CountingViewAttachListener implements ViewAttachListener {
        int attaches;
        int detaches;

        @Override
        public void onAttached(View view) {
            attaches++;
        }

        @Override
        public void onDetached(View view) {
            detaches++;
        }
    }

}
