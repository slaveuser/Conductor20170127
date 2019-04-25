package com.bluelinelabs.conductor;

import android.view.View;

import com.bluelinelabs.conductor.util.ActivityProxy;
import com.bluelinelabs.conductor.util.ListUtils;
import com.bluelinelabs.conductor.util.MockChangeHandler;
import com.bluelinelabs.conductor.util.TestController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RouterChangeHandlerTests {

    private Router router;

    @Before
    public void setup() {
        ActivityProxy activityProxy = new ActivityProxy().create(null).start().resume();
        router = Conductor.attachRouter(activityProxy.getActivity(), activityProxy.getView(), null);
    }

    @Test
    public void testSetRootHandler() {
        MockChangeHandler handler = MockChangeHandler.taggedHandler("root", true);
        TestController rootController = new TestController();
        router.setRoot(RouterTransaction.with(rootController).pushChangeHandler(handler));

        assertTrue(rootController.changeHandlerHistory.isValidHistory);
        assertNull(rootController.changeHandlerHistory.latestFromView());
        assertNotNull(rootController.changeHandlerHistory.latestToView());
        assertEquals(rootController.getView(), rootController.changeHandlerHistory.latestToView());
        assertTrue(rootController.changeHandlerHistory.latestIsPush());
        assertEquals(handler.tag, rootController.changeHandlerHistory.latestChangeHandler().tag);
    }

    @Test
    public void testPushPopHandlers() {
        TestController rootController = new TestController();
        router.setRoot(RouterTransaction.with(rootController).pushChangeHandler(MockChangeHandler.defaultHandler()));
        View rootView = rootController.getView();

        MockChangeHandler pushHandler = MockChangeHandler.taggedHandler("push", true);
        MockChangeHandler popHandler = MockChangeHandler.taggedHandler("pop", true);
        TestController pushController = new TestController();
        router.pushController(RouterTransaction.with(pushController).pushChangeHandler(pushHandler).popChangeHandler(popHandler));

        assertTrue(rootController.changeHandlerHistory.isValidHistory);
        assertTrue(pushController.changeHandlerHistory.isValidHistory);

        assertNotNull(pushController.changeHandlerHistory.latestFromView());
        assertNotNull(pushController.changeHandlerHistory.latestToView());
        assertEquals(rootView, pushController.changeHandlerHistory.latestFromView());
        assertEquals(pushController.getView(), pushController.changeHandlerHistory.latestToView());
        assertTrue(pushController.changeHandlerHistory.latestIsPush());
        assertEquals(pushHandler.tag, pushController.changeHandlerHistory.latestChangeHandler().tag);

        View pushView = pushController.getView();
        router.popController(pushController);

        assertNotNull(pushController.changeHandlerHistory.latestFromView());
        assertNotNull(pushController.changeHandlerHistory.latestToView());
        assertEquals(pushView, pushController.changeHandlerHistory.fromViewAt(1));
        assertEquals(rootController.getView(), pushController.changeHandlerHistory.latestToView());
        assertFalse(pushController.changeHandlerHistory.latestIsPush());
        assertEquals(popHandler.tag, pushController.changeHandlerHistory.latestChangeHandler().tag);
    }

    @Test
    public void testResetRootHandlers() {
        TestController initialController1 = new TestController();
        MockChangeHandler initialPushHandler1 = MockChangeHandler.taggedHandler("initialPush1", true);
        MockChangeHandler initialPopHandler1 = MockChangeHandler.taggedHandler("initialPop1", true);
        router.setRoot(RouterTransaction.with(initialController1).pushChangeHandler(initialPushHandler1).popChangeHandler(initialPopHandler1));
        TestController initialController2 = new TestController();
        MockChangeHandler initialPushHandler2 = MockChangeHandler.taggedHandler("initialPush2", false);
        MockChangeHandler initialPopHandler2 = MockChangeHandler.taggedHandler("initialPop2", false);
        router.pushController(RouterTransaction.with(initialController2).pushChangeHandler(initialPushHandler2).popChangeHandler(initialPopHandler2));

        View initialView1 = initialController1.getView();
        View initialView2 = initialController2.getView();

        TestController newRootController = new TestController();
        MockChangeHandler newRootHandler = MockChangeHandler.taggedHandler("newRootHandler", true);

        router.setRoot(RouterTransaction.with(newRootController).pushChangeHandler(newRootHandler));

        assertTrue(initialController1.changeHandlerHistory.isValidHistory);
        assertTrue(initialController2.changeHandlerHistory.isValidHistory);
        assertTrue(newRootController.changeHandlerHistory.isValidHistory);

        assertEquals(3, initialController1.changeHandlerHistory.size());
        assertEquals(2, initialController2.changeHandlerHistory.size());
        assertEquals(1, newRootController.changeHandlerHistory.size());

        assertNotNull(initialController1.changeHandlerHistory.latestToView());
        assertEquals(newRootController.getView(), initialController1.changeHandlerHistory.latestToView());
        assertEquals(initialView1, initialController1.changeHandlerHistory.latestFromView());
        assertEquals(newRootHandler.tag, initialController1.changeHandlerHistory.latestChangeHandler().tag);
        assertTrue(initialController1.changeHandlerHistory.latestIsPush());

        assertNull(initialController2.changeHandlerHistory.latestToView());
        assertEquals(initialView2, initialController2.changeHandlerHistory.latestFromView());
        assertEquals(newRootHandler.tag, initialController2.changeHandlerHistory.latestChangeHandler().tag);
        assertTrue(initialController2.changeHandlerHistory.latestIsPush());

        assertNotNull(newRootController.changeHandlerHistory.latestToView());
        assertEquals(newRootController.getView(), newRootController.changeHandlerHistory.latestToView());
        assertEquals(initialView1, newRootController.changeHandlerHistory.latestFromView());
        assertEquals(newRootHandler.tag, newRootController.changeHandlerHistory.latestChangeHandler().tag);
        assertTrue(newRootController.changeHandlerHistory.latestIsPush());
    }

    @Test
    public void testSetBackstackHandlers() {
        TestController initialController1 = new TestController();
        MockChangeHandler initialPushHandler1 = MockChangeHandler.taggedHandler("initialPush1", true);
        MockChangeHandler initialPopHandler1 = MockChangeHandler.taggedHandler("initialPop1", true);
        router.setRoot(RouterTransaction.with(initialController1).pushChangeHandler(initialPushHandler1).popChangeHandler(initialPopHandler1));
        TestController initialController2 = new TestController();
        MockChangeHandler initialPushHandler2 = MockChangeHandler.taggedHandler("initialPush2", false);
        MockChangeHandler initialPopHandler2 = MockChangeHandler.taggedHandler("initialPop2", false);
        router.pushController(RouterTransaction.with(initialController2).pushChangeHandler(initialPushHandler2).popChangeHandler(initialPopHandler2));

        View initialView1 = initialController1.getView();
        View initialView2 = initialController2.getView();

        TestController newController1 = new TestController();
        TestController newController2 = new TestController();
        MockChangeHandler setBackstackHandler = MockChangeHandler.taggedHandler("setBackstackHandler", true);

        List<RouterTransaction> newBackstack = ListUtils.listOf(
                RouterTransaction.with(newController1),
                RouterTransaction.with(newController2)
        );

        router.setBackstack(newBackstack, setBackstackHandler);

        assertTrue(initialController1.changeHandlerHistory.isValidHistory);
        assertTrue(initialController2.changeHandlerHistory.isValidHistory);
        assertTrue(newController1.changeHandlerHistory.isValidHistory);

        assertEquals(3, initialController1.changeHandlerHistory.size());
        assertEquals(2, initialController2.changeHandlerHistory.size());
        assertEquals(0, newController1.changeHandlerHistory.size());
        assertEquals(1, newController2.changeHandlerHistory.size());

        assertNotNull(initialController1.changeHandlerHistory.latestToView());
        assertEquals(newController2.getView(), initialController1.changeHandlerHistory.latestToView());
        assertEquals(initialView1, initialController1.changeHandlerHistory.latestFromView());
        assertEquals(setBackstackHandler.tag, initialController1.changeHandlerHistory.latestChangeHandler().tag);
        assertTrue(initialController1.changeHandlerHistory.latestIsPush());

        assertNull(initialController2.changeHandlerHistory.latestToView());
        assertEquals(initialView2, initialController2.changeHandlerHistory.latestFromView());
        assertEquals(setBackstackHandler.tag, initialController2.changeHandlerHistory.latestChangeHandler().tag);
        assertTrue(initialController2.changeHandlerHistory.latestIsPush());

        assertNotNull(newController2.changeHandlerHistory.latestToView());
        assertEquals(newController2.getView(), newController2.changeHandlerHistory.latestToView());
        assertEquals(initialView1, newController2.changeHandlerHistory.latestFromView());
        assertEquals(setBackstackHandler.tag, newController2.changeHandlerHistory.latestChangeHandler().tag);
        assertTrue(newController2.changeHandlerHistory.latestIsPush());
    }

    @Test
    public void testSetBackstackWithTwoVisibleHandlers() {
        TestController initialController1 = new TestController();
        MockChangeHandler initialPushHandler1 = MockChangeHandler.taggedHandler("initialPush1", true);
        MockChangeHandler initialPopHandler1 = MockChangeHandler.taggedHandler("initialPop1", true);
        router.setRoot(RouterTransaction.with(initialController1).pushChangeHandler(initialPushHandler1).popChangeHandler(initialPopHandler1));
        TestController initialController2 = new TestController();
        MockChangeHandler initialPushHandler2 = MockChangeHandler.taggedHandler("initialPush2", false);
        MockChangeHandler initialPopHandler2 = MockChangeHandler.taggedHandler("initialPop2", false);
        router.pushController(RouterTransaction.with(initialController2).pushChangeHandler(initialPushHandler2).popChangeHandler(initialPopHandler2));

        View initialView1 = initialController1.getView();
        View initialView2 = initialController2.getView();

        TestController newController1 = new TestController();
        TestController newController2 = new TestController();
        MockChangeHandler setBackstackHandler = MockChangeHandler.taggedHandler("setBackstackHandler", true);

        List<RouterTransaction> newBackstack = ListUtils.listOf(
                RouterTransaction.with(newController1),
                RouterTransaction.with(newController2).pushChangeHandler(MockChangeHandler.noRemoveViewOnPushHandler())
        );

        router.setBackstack(newBackstack, setBackstackHandler);

        assertTrue(initialController1.changeHandlerHistory.isValidHistory);
        assertTrue(initialController2.changeHandlerHistory.isValidHistory);
        assertTrue(newController1.changeHandlerHistory.isValidHistory);

        assertEquals(3, initialController1.changeHandlerHistory.size());
        assertEquals(2, initialController2.changeHandlerHistory.size());
        assertEquals(2, newController1.changeHandlerHistory.size());
        assertEquals(1, newController2.changeHandlerHistory.size());

        assertNotNull(initialController1.changeHandlerHistory.latestToView());
        assertEquals(newController1.getView(), initialController1.changeHandlerHistory.latestToView());
        assertEquals(initialView1, initialController1.changeHandlerHistory.latestFromView());
        assertEquals(setBackstackHandler.tag, initialController1.changeHandlerHistory.latestChangeHandler().tag);
        assertTrue(initialController1.changeHandlerHistory.latestIsPush());

        assertNull(initialController2.changeHandlerHistory.latestToView());
        assertEquals(initialView2, initialController2.changeHandlerHistory.latestFromView());
        assertEquals(setBackstackHandler.tag, initialController2.changeHandlerHistory.latestChangeHandler().tag);
        assertTrue(initialController2.changeHandlerHistory.latestIsPush());

        assertNotNull(newController1.changeHandlerHistory.latestToView());
        assertEquals(newController1.getView(), newController1.changeHandlerHistory.toViewAt(0));
        assertEquals(newController2.getView(), newController1.changeHandlerHistory.latestToView());
        assertEquals(initialView1, newController1.changeHandlerHistory.fromViewAt(0));
        assertEquals(newController1.getView(), newController1.changeHandlerHistory.latestFromView());
        assertEquals(setBackstackHandler.tag, newController1.changeHandlerHistory.latestChangeHandler().tag);
        assertTrue(newController1.changeHandlerHistory.latestIsPush());

        assertNotNull(newController2.changeHandlerHistory.latestToView());
        assertEquals(newController2.getView(), newController2.changeHandlerHistory.latestToView());
        assertEquals(newController1.getView(), newController2.changeHandlerHistory.latestFromView());
        assertEquals(setBackstackHandler.tag, newController2.changeHandlerHistory.latestChangeHandler().tag);
        assertTrue(newController2.changeHandlerHistory.latestIsPush());
    }

}
