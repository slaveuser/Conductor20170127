package com.bluelinelabs.conductor;

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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RouterTests {

    private Router router;

    @Before
    public void setup() {
        ActivityProxy activityProxy = new ActivityProxy().create(null).start().resume();
        router = Conductor.attachRouter(activityProxy.getActivity(), activityProxy.getView(), null);
    }

    @Test
    public void testSetRoot() {
        String rootTag = "root";

        Controller rootController = new TestController();

        assertFalse(router.hasRootController());

        router.setRoot(RouterTransaction.with(rootController).tag(rootTag));

        assertTrue(router.hasRootController());

        assertEquals(rootController, router.getControllerWithTag(rootTag));
    }

    @Test
    public void testSetNewRoot() {
        String oldRootTag = "oldRoot";
        String newRootTag = "newRoot";

        Controller oldRootController = new TestController();
        Controller newRootController = new TestController();

        router.setRoot(RouterTransaction.with(oldRootController).tag(oldRootTag));
        router.setRoot(RouterTransaction.with(newRootController).tag(newRootTag));

        assertNull(router.getControllerWithTag(oldRootTag));
        assertEquals(newRootController, router.getControllerWithTag(newRootTag));
    }

    @Test
    public void testGetByInstanceId() {
        Controller controller = new TestController();

        router.pushController(RouterTransaction.with(controller));

        assertEquals(controller, router.getControllerWithInstanceId(controller.getInstanceId()));
        assertNull(router.getControllerWithInstanceId("fake id"));
    }

    @Test
    public void testGetByTag() {
        String controller1Tag = "controller1";
        String controller2Tag = "controller2";

        Controller controller1 = new TestController();
        Controller controller2 = new TestController();

        router.pushController(RouterTransaction.with(controller1)
                .tag(controller1Tag));

        router.pushController(RouterTransaction.with(controller2)
                .tag(controller2Tag));

        assertEquals(controller1, router.getControllerWithTag(controller1Tag));
        assertEquals(controller2, router.getControllerWithTag(controller2Tag));
    }

    @Test
    public void testPushPopControllers() {
        String controller1Tag = "controller1";
        String controller2Tag = "controller2";

        Controller controller1 = new TestController();
        Controller controller2 = new TestController();

        router.pushController(RouterTransaction.with(controller1)
                .tag(controller1Tag));

        assertEquals(1, router.getBackstackSize());

        router.pushController(RouterTransaction.with(controller2)
                .tag(controller2Tag));

        assertEquals(2, router.getBackstackSize());

        router.popCurrentController();

        assertEquals(1, router.getBackstackSize());

        assertEquals(controller1, router.getControllerWithTag(controller1Tag));
        assertNull(router.getControllerWithTag(controller2Tag));

        router.popCurrentController();

        assertEquals(0, router.getBackstackSize());

        assertNull(router.getControllerWithTag(controller1Tag));
        assertNull(router.getControllerWithTag(controller2Tag));
    }

    @Test
    public void testPopToTag() {
        String controller1Tag = "controller1";
        String controller2Tag = "controller2";
        String controller3Tag = "controller3";
        String controller4Tag = "controller4";

        Controller controller1 = new TestController();
        Controller controller2 = new TestController();
        Controller controller3 = new TestController();
        Controller controller4 = new TestController();

        router.pushController(RouterTransaction.with(controller1)
                .tag(controller1Tag));

        router.pushController(RouterTransaction.with(controller2)
                .tag(controller2Tag));

        router.pushController(RouterTransaction.with(controller3)
                .tag(controller3Tag));

        router.pushController(RouterTransaction.with(controller4)
                .tag(controller4Tag));

        router.popToTag(controller2Tag);

        assertEquals(2, router.getBackstackSize());
        assertEquals(controller1, router.getControllerWithTag(controller1Tag));
        assertEquals(controller2, router.getControllerWithTag(controller2Tag));
        assertNull(router.getControllerWithTag(controller3Tag));
        assertNull(router.getControllerWithTag(controller4Tag));
    }

    @Test
    public void testPopNonCurrent() {
        String controller1Tag = "controller1";
        String controller2Tag = "controller2";
        String controller3Tag = "controller3";

        Controller controller1 = new TestController();
        Controller controller2 = new TestController();
        Controller controller3 = new TestController();

        router.pushController(RouterTransaction.with(controller1)
                .tag(controller1Tag));

        router.pushController(RouterTransaction.with(controller2)
                .tag(controller2Tag));

        router.pushController(RouterTransaction.with(controller3)
                .tag(controller3Tag));

        router.popController(controller2);

        assertEquals(2, router.getBackstackSize());
        assertEquals(controller1, router.getControllerWithTag(controller1Tag));
        assertNull(router.getControllerWithTag(controller2Tag));
        assertEquals(controller3, router.getControllerWithTag(controller3Tag));
    }

    @Test
    public void testSetBackstack() {
        RouterTransaction rootTransaction = RouterTransaction.with(new TestController());
        RouterTransaction middleTransaction = RouterTransaction.with(new TestController());
        RouterTransaction topTransaction = RouterTransaction.with(new TestController());

        List<RouterTransaction> backstack = ListUtils.listOf(rootTransaction, middleTransaction, topTransaction);
        router.setBackstack(backstack, null);

        assertEquals(3, router.getBackstackSize());

        List<RouterTransaction> fetchedBackstack = router.getBackstack();
        assertEquals(rootTransaction, fetchedBackstack.get(0));
        assertEquals(middleTransaction, fetchedBackstack.get(1));
        assertEquals(topTransaction, fetchedBackstack.get(2));
    }

    @Test
    public void testNewSetBackstack() {
        router.setRoot(RouterTransaction.with(new TestController()));

        assertEquals(1, router.getBackstackSize());

        RouterTransaction rootTransaction = RouterTransaction.with(new TestController());
        RouterTransaction middleTransaction = RouterTransaction.with(new TestController());
        RouterTransaction topTransaction = RouterTransaction.with(new TestController());

        List<RouterTransaction> backstack = ListUtils.listOf(rootTransaction, middleTransaction, topTransaction);
        router.setBackstack(backstack, null);

        assertEquals(3, router.getBackstackSize());

        List<RouterTransaction> fetchedBackstack = router.getBackstack();
        assertEquals(rootTransaction, fetchedBackstack.get(0));
        assertEquals(middleTransaction, fetchedBackstack.get(1));
        assertEquals(topTransaction, fetchedBackstack.get(2));

        assertEquals(router, rootTransaction.controller.getRouter());
        assertEquals(router, middleTransaction.controller.getRouter());
        assertEquals(router, topTransaction.controller.getRouter());
    }

    @Test
    public void testNewSetBackstackWithNoRemoveViewOnPush() {
        RouterTransaction oldRootTransaction = RouterTransaction.with(new TestController());
        RouterTransaction oldTopTransaction = RouterTransaction.with(new TestController()).pushChangeHandler(MockChangeHandler.noRemoveViewOnPushHandler());

        router.setRoot(oldRootTransaction);
        router.pushController(oldTopTransaction);
        assertEquals(2, router.getBackstackSize());

        assertTrue(oldRootTransaction.controller.isAttached());
        assertTrue(oldTopTransaction.controller.isAttached());

        RouterTransaction rootTransaction = RouterTransaction.with(new TestController());
        RouterTransaction middleTransaction = RouterTransaction.with(new TestController()).pushChangeHandler(MockChangeHandler.noRemoveViewOnPushHandler());
        RouterTransaction topTransaction = RouterTransaction.with(new TestController()).pushChangeHandler(MockChangeHandler.noRemoveViewOnPushHandler());

        List<RouterTransaction> backstack = ListUtils.listOf(rootTransaction, middleTransaction, topTransaction);
        router.setBackstack(backstack, null);

        assertEquals(3, router.getBackstackSize());

        List<RouterTransaction> fetchedBackstack = router.getBackstack();
        assertEquals(rootTransaction, fetchedBackstack.get(0));
        assertEquals(middleTransaction, fetchedBackstack.get(1));
        assertEquals(topTransaction, fetchedBackstack.get(2));

        assertFalse(oldRootTransaction.controller.isAttached());
        assertFalse(oldTopTransaction.controller.isAttached());
        assertTrue(rootTransaction.controller.isAttached());
        assertTrue(middleTransaction.controller.isAttached());
        assertTrue(topTransaction.controller.isAttached());
    }

    @Test
    public void testReplaceTopController() {
        RouterTransaction rootTransaction = RouterTransaction.with(new TestController());
        RouterTransaction topTransaction = RouterTransaction.with(new TestController());

        List<RouterTransaction> backstack = ListUtils.listOf(rootTransaction, topTransaction);
        router.setBackstack(backstack, null);

        assertEquals(2, router.getBackstackSize());

        List<RouterTransaction> fetchedBackstack = router.getBackstack();
        assertEquals(rootTransaction, fetchedBackstack.get(0));
        assertEquals(topTransaction, fetchedBackstack.get(1));

        RouterTransaction newTopTransaction = RouterTransaction.with(new TestController());
        router.replaceTopController(newTopTransaction);

        assertEquals(2, router.getBackstackSize());

        fetchedBackstack = router.getBackstack();
        assertEquals(rootTransaction, fetchedBackstack.get(0));
        assertEquals(newTopTransaction, fetchedBackstack.get(1));
    }

    @Test
    public void testReplaceTopControllerWithNoRemoveViewOnPush() {
        RouterTransaction rootTransaction = RouterTransaction.with(new TestController());
        RouterTransaction topTransaction = RouterTransaction.with(new TestController()).pushChangeHandler(MockChangeHandler.noRemoveViewOnPushHandler());

        List<RouterTransaction> backstack = ListUtils.listOf(rootTransaction, topTransaction);
        router.setBackstack(backstack, null);

        assertEquals(2, router.getBackstackSize());

        assertTrue(rootTransaction.controller.isAttached());
        assertTrue(topTransaction.controller.isAttached());

        List<RouterTransaction> fetchedBackstack = router.getBackstack();
        assertEquals(rootTransaction, fetchedBackstack.get(0));
        assertEquals(topTransaction, fetchedBackstack.get(1));

        RouterTransaction newTopTransaction = RouterTransaction.with(new TestController()).pushChangeHandler(MockChangeHandler.noRemoveViewOnPushHandler());
        router.replaceTopController(newTopTransaction);
        newTopTransaction.pushChangeHandler().completeImmediately();

        assertEquals(2, router.getBackstackSize());

        fetchedBackstack = router.getBackstack();
        assertEquals(rootTransaction, fetchedBackstack.get(0));
        assertEquals(newTopTransaction, fetchedBackstack.get(1));

        assertTrue(rootTransaction.controller.isAttached());
        assertFalse(topTransaction.controller.isAttached());
        assertTrue(newTopTransaction.controller.isAttached());
    }

}
