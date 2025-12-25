package org.kde.bettercounter

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.bettercounter.persistence.CounterMetadata
import org.kde.bettercounter.persistence.CounterColor
import org.kde.bettercounter.persistence.Interval
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class ViewModelConcurrencyTest {

    private lateinit var viewModel: ViewModel
    private lateinit var testDispatcher: TestCoroutineDispatcher

    @Before
    fun setUp() {
        testDispatcher = TestCoroutineDispatcher()
        Dispatchers.setMain(testDispatcher)
        
        val context = ApplicationProvider.getApplicationContext<Context>()
        viewModel = ViewModel(context as Application)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }

    @Test
    fun testConcurrentObserverAccess() = runBlockingTest {
        // 模拟多个线程同时访问 counterObservers
        val latch = CountDownLatch(3)
        
        // 创建多个观察者
        val observer1 = object : ViewModel.CounterObserver {
            override fun onInitialCountersLoaded() {}
            override fun onCounterAdded(counterName: String) {}
            override fun onCounterRemoved(counterName: String) {}
            override fun onCounterRenamed(oldName: String, newName: String) {}
            override fun onCounterDecremented(counterName: String, oldEntryDate: java.util.Date) {}
        }
        
        val observer2 = object : ViewModel.CounterObserver {
            override fun onInitialCountersLoaded() {}
            override fun onCounterAdded(counterName: String) {}
            override fun onCounterRemoved(counterName: String) {}
            override fun onCounterRenamed(oldName: String, newName: String) {}
            override fun onCounterDecremented(counterName: String, oldEntryDate: java.util.Date) {}
        }
        
        val observer3 = object : ViewModel.CounterObserver {
            override fun onInitialCountersLoaded() {}
            override fun onCounterAdded(counterName: String) {}
            override fun onCounterRemoved(counterName: String) {}
            override fun onCounterRenamed(oldName: String, newName: String) {}
            override fun onCounterDecremented(counterName: String, oldEntryDate: java.util.Date) {}
        }

        // 并发添加观察者
        launch(Dispatchers.Main) {
            viewModel.observeCounterChange(observer1)
            latch.countDown()
        }
        
        launch(Dispatchers.Main) {
            viewModel.observeCounterChange(observer2)
            latch.countDown()
        }
        
        launch(Dispatchers.Main) {
            viewModel.observeCounterChange(observer3)
            latch.countDown()
        }

        // 等待所有操作完成
        latch.await(5, TimeUnit.SECONDS)
        
        // 并发移除观察者
        val removeLatch = CountDownLatch(3)
        
        launch(Dispatchers.Main) {
            viewModel.removeCounterChangeObserver(observer1)
            removeLatch.countDown()
        }
        
        launch(Dispatchers.Main) {
            viewModel.removeCounterChangeObserver(observer2)
            removeLatch.countDown()
        }
        
        launch(Dispatchers.Main) {
            viewModel.removeCounterChangeObserver(observer3)
            removeLatch.countDown()
        }

        removeLatch.await(5, TimeUnit.SECONDS)
        
        // 如果没有抛出 ConcurrentModificationException，测试通过
    }

    @Test
    fun testConcurrentCounterOperations() = runBlockingTest {
        // 添加一个计数器
        val counter = CounterMetadata(
            name = "TestCounter",
            color = CounterColor.getDefault(ApplicationProvider.getApplicationContext()),
            category = "默认",
            interval = Interval.DAY,
            goal = 10
        )
        
        viewModel.addCounter(counter)
        
        // 模拟并发操作
        val latch = CountDownLatch(3)
        
        // 并发增加计数器
        launch(Dispatchers.IO) {
            viewModel.incrementCounter("TestCounter")
            latch.countDown()
        }
        
        // 并发减少计数器
        launch(Dispatchers.IO) {
            viewModel.decrementCounter("TestCounter")
            latch.countDown()
        }
        
        // 并发刷新观察者
        launch(Dispatchers.IO) {
            viewModel.refreshAllObservers()
            latch.countDown()
        }

        latch.await(5, TimeUnit.SECONDS)
        
        // 如果没有抛出 ConcurrentModificationException，测试通过
    }
} 