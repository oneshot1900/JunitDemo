package com.test.junittest;


import android.app.Application;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.text.TextUtils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 每个@Test是一个单元测试模块，可以右击一个模块run，可以右击这个文件run所有模块
 */
//使用Robolectric必须加上该注解，可通过External libraries-org.robolectric:shadows-framework-3.8@jar-org/shadows查看实现的Android常用类
//manifest配合constants = BuildConfig.class一起使用，会从build/intermediates/bundles/debug/$manifest去找，若没有自己创建一个
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, manifest = "src/test/AndroidManifest.xml", application = BaseTest.TestApp.class, shadows = {BaseTest.ShadowMobUtils.class}/* ,sdk = 21*/)
public class BaseTest {
    public static final String TAG = "======TestLog=====";
    public Application shadowApplication;

    //基类在before中进行了mock初始化，这样mock的注解才能生效
    @Before
    public void init() {
        log("BaseTest#init");
        MockitoAnnotations.initMocks(this);
        shadowApplication = RuntimeEnvironment.application;
    }

    //单元测试中System.out可以打印出来
    public static void log(String text) {
        System.out.println(TAG + text);
    }

    //若一个测试类里没有任务会报错。
    @Test
    public void emptyTest() {

    }

    //////////////////////////////////////自定义Application////////////////////////////////////
    public static class TestApp extends Application {

        @Override
        public void onCreate() {
            super.onCreate();
            log("TestApp#onCreate");
        }
    }

    /////////////////////////////////////替换指定方法内容(Hook)///////////////////////////////////
    //通过Robolectric的shadow功能，替换掉指定方法的内容，可用于替换入Invoker接口，来实现方法被调用的通知,(由于是替换，原有方法内容需要复制进来)
    public static class ShadowUtils {
        private static HashMap<String, Invoker> hashMap = new HashMap<String, Invoker>();

        //对指定类的指定方法，植入回调
        public static void setInvoker(Class clz, String methodName, ShadowUtils.Invoker invoker) {
            if (invoker == null || clz == null || methodName == null) {
                return;
            }
            String key = clz.getName() + "#" + methodName;
            if (!hashMap.containsKey(key)) {
                hashMap.put(key, invoker);
            }
        }

        public static void onListen(Class clz, Object... args) {
            if (clz == null) {
                return;
            }
            Implements anImplements = (Implements) clz.getAnnotation(Implements.class);
            String className = anImplements.value().getName();
            String methodName = Thread.currentThread().getStackTrace()[2].getMethodName();
            if (hashMap != null) {
                String key = className + "#" + methodName;
                Invoker invoker = hashMap.get(key);
                if (invoker != null) {
                    invoker.invoke(args);
                }
            }
        }

        public interface Invoker {
            void invoke(Object... args);
        }
    }

    //Shadow例子
    //(1)注解需要替换的方法所在类，必须实现
    @Implements(value = MobUtils.class)
    //(2)在测试类的Config注解中添加shadows = {MockTest.ShadowTestUtil.class},必须实现
    public static class ShadowMobUtils{

        //(3)注解需要替换的方法，相当于覆写，必须实现
        @Implementation
        public int add(int i, int j) {
            //原方法内容
            int r = i + j;
            //插入的内容
            r = 2 * r;
            //增加回调
            //(4)该方法的传参，需要动态改
            //增加回调
            ShadowUtils.onListen(
                    getClass()
                    , i,j);
            return r;
        }
    }

    //////////////////////////////////////单元测试常用功能例子///////////////////////////////////////////////

    //mock可以模拟一个对象，可以注解变量，或者动态mock（xx.class）
    //mock创建了一个目标的代理类，通过byte-buddy框架生成代理类的字节码，代理类里有目标类相同的方法，都是空实现并且有一个拦截器进行处理返回。
    //各种参数都会保存在内部，拦截时返回
    @Mock
    MobUtils bean;
    //injectmocks会执行代码
    @InjectMocks
    MobUtils beanInject;

    MobUtils realBean = new MobUtils();

    //观察mock和正常实例化区别
    @Test
    public void testMock() {
        log("!!!!!!!!!Test doTest start!!!!!!!!!!");

        //对比mock和正常实例化区别，注解和java的mock出来都没有地址，实例化有
        log("#testMock annotation mock bean = " + bean);
        log("#testMock mock bean = " + mock(MobUtils.class));
        log("#testMock injectmock bean = " + beanInject);
        log("#testMock InjectMock = " + beanInject.add(1, 1));
        log("#testMock realBean = " + realBean);
    }

    //常用Assert方法
    @Test
    public void testAssert() {
        log("!!!!!!!!!Test testAssert start!!!!!!!!!!");

        assertThat(MobUtils.isEmpty(null), is(true));

        List<Integer> scores = Arrays.asList(99, 100, 101, 105);
        assertThat(scores, hasSize(4));
        assertThat(scores, hasItems(100, 101));
        assertThat(scores, everyItem(greaterThan(90)));
        assertThat(scores, everyItem(lessThan(200)));

        // String
        assertThat("", isEmptyString());
        assertThat(null, isEmptyOrNullString());

        // Array
        Integer[] marks = {1, 2, 3};
        assertThat(marks, arrayWithSize(3));
        assertThat(marks, arrayContainingInAnyOrder(2, 3, 1));
    }

    //verify方法判断，该方法调用之前是否符合设定的条件，不符合则抛异常，不继续走
    @Test
    public void testVerify() {
        log("!!!!!!!!!Test testVerify start!!!!!!!!!!");
        MobUtils mobUtilsMock = mock(MobUtils.class);

        // 验证是否没有调用过
        verify(mobUtilsMock, never()).checkLength(anyString());

        mobUtilsMock.checkLength("1234");
        //验证参数调用情况，ArgumentCaptor配合verify
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mobUtilsMock).checkLength(captor.capture());
        assertThat("1234", is(captor.getValue()));

        // 验证是否这样调用过,且默认只调用了1次
        verify(mobUtilsMock).checkLength("1234");
        mobUtilsMock.checkLength("1234");
        // 验证是否调用了2次
        verify(mobUtilsMock, times(2)).checkLength("1234");

        // 验证是否至少调用过2次
        verify(mobUtilsMock, atLeast(2)).checkLength("1234");

        mobUtilsMock.checkLength("1234");
        // 验证是否最多调用过4次
        verify(mobUtilsMock, atMost(3)).checkLength("1234");
    }

    //模拟方法返回，when then的用法
    @Test
    public void testWhenThen() {
        log("!!!!!!!!!Test testWhenThen start!!!!!!!!!!");
        MobUtils mobUtilsMock = mock(MobUtils.class);

        // when thenReturn，指定条件下的常用类型返回，连续调用，第一次执行第一个thenReturn，之后执行第二个
        //如果不关注传参的值，可以使用anyXX来代替
        //如果多个参数时，有其中一个使用anyXX这类，那所有参数都必须使用匹配matchers类型，否则报错
        //由于第一个参数是hashmap，而只有anyMap，因此用eq(xx)代替,eq也是matchers
        when(mobUtilsMock.add(anyInt(), anyInt())).thenReturn(10).thenReturn(12);
        log("#doTest use when thenReturn first time = " + mobUtilsMock.add(1, 1));
        log("#doTest use when thenReturn second time = " + mobUtilsMock.add(1, 1));
        log("#doTest use when thenReturn third time = " + mobUtilsMock.add(1, 1));

        // mock对象，当调用的时候，什么都不做
        mobUtilsMock.checkLength("1234");

        //when thenAnswer，指定条件下的定制返回
        when(mobUtilsMock.add(anyInt(), anyInt())).thenAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
                log("use answer");
                return 10;
            }
        });
        log("#doTest use when thenAnswer add 1+1 = " + mobUtilsMock.add(1, 1));


        // 当调用的时候，抛NullPointerException异常
//        doThrow(new NullPointerException()).when(mobUtilsMock).add(1,1);
//        mobUtilsMock.add(1,1));
    }

    //spy的用法，和mock的区别：mock的对象中的方法不会真正的执行，因此有返回类型默认返回null；而spy则会执行
    @Test
    public void testSpy() {
        log("!!!!!!!!!Test testSpy start!!!!!!!!!!");
        MobUtils mobUtilsMock = spy(MobUtils.class);
        mobUtilsMock.checkLength("1122");

        //spy对象必须先return再when，先when会真实执行，可能报错
        doReturn(3).when(mobUtilsMock).add(1, 1);
        log("spy return = " + mobUtilsMock.add(1, 1));
    }

    //Mockito不能模拟静态方法，测试的时候需要写一个非静态方法包装静态方法
    @Test
    public void testStaticMethod() {
        log("!!!!!!!!!Test testStaticMethod start!!!!!!!!!!");
        StaticMethodWrapper wrapper = mock(StaticMethodWrapper.class);
        when(wrapper.checkLength("12")).thenReturn(true);
        log("invoke static method and use when then = " + wrapper.checkLength("12"));
    }

    class StaticMethodWrapper {
        public boolean checkLength(String text) {
            return MobUtils.isEmpty(text);
        }
    }

    //PowerMock封装了Mockito,支持静态、私有、final方法的模拟
    @RunWith(PowerMockRunner.class)
    @PrepareForTest({MobUtils.class})
    public static class PowerMockTest {

        @Test
        public void testPowerMock() {
            log("!!!!!!!!!Test testPowerMock start!!!!!!!!!!");
            //mock静态方法
            PowerMockito.mockStatic(MobUtils.class);
            PowerMockito.when(MobUtils.isEmpty(anyString())).thenReturn(true);
            log("powermock call static method result = " + MobUtils.isEmpty("1"));

            //mock私有方法
            MobUtils mobUtilsMock = PowerMockito.mock(MobUtils.class);
            try {
                PowerMockito.when(mobUtilsMock, "minus").thenReturn(1);
            } catch (Exception e) {
                e.printStackTrace();
            }
            log("powermock call private method result = " + (int) ReflectionHelpers.callInstanceMethod(mobUtilsMock, "minus", new ReflectionHelpers.ClassParameter(int.class, 4), new ReflectionHelpers.ClassParameter(int.class, 1)));
        }
    }

    //Robolectric提供了Android常用类，最常用的是application、生成activity、指定sdk、自定义application、shadow等
    @Test
    public void testRobol() {
        log("!!!!!!!!!Test testRobol start!!!!!!!!!!");
        log("testConfig sdk = " + Build.VERSION.SDK_INT);
        //获取application
        WifiManager wifiManager = (WifiManager)  shadowApplication.getSystemService(Context.WIFI_SERVICE);
        log(wifiManager.getConnectionInfo().getMacAddress());

        //robol模拟activity，点击，(如果控件是写在布局中，会报错，加了unitTests.includeAndroidResources true也无效，因此将控件是动态写的)
//        MainActivity activity = Robolectric.setupActivity(MainActivity.class);
//        activity.findViewById(R.id.button1).performClick();

        //读取配置的manifest
//        PackageInfo pi = null;
//        try {
//            pi = shadowApplication.getPackageManager().getPackageInfo(shadowApplication.getPackageName(), PackageManager.GET_META_DATA);
//            Bundle metaData = pi.applicationInfo.metaData;
//            String key=metaData.getString("key");
//            log("key = "+key);
//        } catch (PackageManager.NameNotFoundException e) {
//            e.printStackTrace();
//        }

    }

    //验证Robolectric的shadow功能
    @Test
    public void shadowExample() {
        MobUtils mobUtils = new MobUtils();
        ShadowUtils.setInvoker(MobUtils.class, "add", new ShadowUtils.Invoker() {
            @Override
            public void invoke(Object... args) {
                log("shadow success , receive args = " + args[0] + " " + args[1]);
            }
        });
        //原先返回2，替换后返回4
        Assert.assertEquals(4, mobUtils.add(1, 1));
    }

    //////////////////////////////////////简单的项目类///////////////////////////////////////
    public static class MobUtils {
        public MobUtils() {
            log("MobUtils#construct");
        }

        public int add(int i, int j) {
            return i + j;
        }

        public boolean checkLength(String text) {
            System.out.println("input is " + text);
            if (!TextUtils.isEmpty(text) && text.length() == 4) {
                return true;
            }
            return false;
        }

        public static boolean isEmpty(String text) {
            return TextUtils.isEmpty(text);
        }

        private int minus(int i, int j) {
            return i - j;
        }
    }

}
