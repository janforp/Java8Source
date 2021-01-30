package util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;

/**
 * A cancellable asynchronous computation.  This class provides a base
 * implementation of {@link Future}, with methods to start and cancel
 * a computation, query to see if the computation is complete, and
 * retrieve the result of the computation.  The result can only be
 * retrieved when the computation has completed; the {@code get}
 * methods will block if the computation has not yet completed.  Once
 * the computation has completed, the computation cannot be restarted
 * or cancelled (unless the computation is invoked using
 * {@link #runAndReset}).
 *
 * <p>A {@code FutureTask} can be used to wrap a {@link Callable} or
 * {@link Runnable} object.  Because {@code FutureTask} implements
 * {@code Runnable}, a {@code FutureTask} can be submitted to an
 * {@link Executor} for execution.
 *
 * <p>In addition to serving as a standalone class, this class provides
 * {@code protected} functionality that may be useful when creating
 * customized task classes.
 *
 * @param <V> The result type returned by this FutureTask's {@code get} methods
 * @author Doug Lea
 * @since 1.5
 */
@SuppressWarnings("all")
public class FutureTask<V> implements RunnableFuture<V> {
    /*
     * Revision notes: This differs from previous versions of this
     * class that relied on AbstractQueuedSynchronizer, mainly to
     * avoid surprising users about retaining interrupt status during
     * cancellation races. Sync control in the current design relies
     * on a "state" field updated via CAS to track completion, along
     * with a simple Treiber stack to hold waiting threads.
     *
     * Style note: As usual, we bypass overhead of using
     * AtomicXFieldUpdaters and instead directly use Unsafe intrinsics.
     *
     * 修订说明：这与依赖AbstractQueuedSynchronizer的此类的早期版本不同，主要是为了避免用户对取消比赛期间保留中断状态感到惊讶。
     * 当前设计中的同步控制依赖于通过CAS更新的“状态”字段来跟踪完成情况，以及一个简单的Treiber堆栈来保存等待线程。样式说明：与往常一样，
     * 我们绕过了使用AtomicXFieldUpdaters的开销，而是直接使用Unsafe内部函数。
     */

    /**
     * The run state of this task, initially NEW.  The run state
     * transitions to a terminal state only in methods set,
     * setException, and cancel.  During completion, state may take on
     * transient values of COMPLETING (while outcome is being set) or
     * INTERRUPTING (only while interrupting the runner to satisfy a
     * cancel(true)). Transitions from these intermediate to final
     * states use cheaper ordered/lazy writes because values are unique
     * and cannot be further modified.
     *
     * Possible state transitions:
     * NEW -> COMPLETING -> NORMAL
     * NEW -> COMPLETING -> EXCEPTIONAL
     * NEW -> CANCELLED
     * NEW -> INTERRUPTING -> INTERRUPTED
     */
    //表示当前task状态
    private volatile int state;

    //当前任务尚未执行
    private static final int NEW = 0;

    //当前任务正在结束，稍微完全结束，一种临界状态
    private static final int COMPLETING = 1;

    //当前任务正常结束
    private static final int NORMAL = 2;

    //当前任务执行过程中发生了异常。 内部封装的 callable.run() 向上抛出异常了
    private static final int EXCEPTIONAL = 3;

    //当前任务被取消
    private static final int CANCELLED = 4;

    //当前任务中断中..
    private static final int INTERRUPTING = 5;

    /**
     * 中断只是线程的一个标记,不是把线程给断了，只是通知一下
     */
    //当前任务已中断
    private static final int INTERRUPTED = 6;

    /**
     * The underlying callable; nulled out after running
     *
     * submit(runnable/callable)   runnable 使用 装饰者模式 伪装成 Callable了。
     */
    private Callable<V> callable;

    /**
     * The result to return or exception to throw from get()
     */
    //正常情况下：任务正常执行结束，outcome保存执行结果。 callable 返回值。
    //非正常情况：callable向上抛出异常，outcome保存异常
    private Object outcome; // non-volatile, protected by state reads/writes

    /**
     * The thread running the callable; CASed during run()
     */
    //当前任务被线程执行期间，保存当前执行任务的线程对象引用。
    private volatile Thread runner;

    /**
     * Treiber stack of waiting threads
     */
    //因为会有很多线程去get当前任务的结果，所以 这里使用了一种数据结构 stack 头插 头取 的一个队列。
    private volatile WaitNode waiters;

    /**
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Callable}.
     *
     * @param callable the callable task
     * @throws NullPointerException if the callable is null
     */
    public FutureTask(Callable<V> callable) {
        if (callable == null) {
            throw new NullPointerException();
        }
        //callable就是程序员自己实现的业务类
        this.callable = callable;
        //设置当前任务状态为 NEW 确保可调用的可见性
        this.state = NEW;       // ensure visibility of callable
    }

    /**
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Runnable}, and arrange that {@code get} will return the
     * given result on successful completion.
     *
     * @param runnable the runnable task
     * @param result the result to return on successful completion. If
     * you don't need a particular result, consider using
     * constructions of the form:
     * {@code Future<?> f = new FutureTask<Void>(runnable, null)}
     * @throws NullPointerException if the runnable is null
     */
    public FutureTask(Runnable runnable, V result) {
        //使用装饰者模式 /适配器模式 (RunnableAdapter)将runnable转换为了 callable接口，外部线程 通过get获取
        //当前任务执行结果时，结果可能为 null 也可能为 传进来的值。
        this.callable = Executors.callable(runnable, result);
        this.state = NEW;       // ensure visibility of callable
    }

    /**
     * Returns result or throws exception for completed task.
     *
     * @param s completed state value
     */
    @SuppressWarnings("unchecked")
    private V report(int s) throws ExecutionException {
        //正常情况下，outcome 保存的是callable运行结束的结果
        //非正常，保存的是 callable 抛出的异常。
        Object x = outcome;

        //条件成立：当前任务状态正常结束
        if (s == NORMAL) {

            //直接返回callable运算结果
            return (V) x;
        }

        //被取消状态
        //虽然中断状态也是满足条件的，但是中断的时候是直接抛出了中断异常，不会指向该方法，而是在get的时候直接就抛出去了
        if (s >= CANCELLED) {
            throw new CancellationException();
        }

        //执行到这，说明callable接口实现中，是有bug的...
        throw new ExecutionException((Throwable) x);
    }

    public boolean isCancelled() {
        return state >= CANCELLED;
    }

    public boolean isDone() {
        return state != NEW;
    }

    /**
     * @param mayInterruptIfRunning true 则发送一个中断信号
     * @return
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (!(state == NEW//条件一：state == NEW 成立 表示当前任务处于运行中 或者 处于线程池 任务队列中..
                &&
                // 条件成立：说明修改状态成功，可以去执行下面逻辑了，否则 返回false 表示cancel失败。
                UNSAFE.compareAndSwapInt(this, stateOffset, NEW, mayInterruptIfRunning ? INTERRUPTING : CANCELLED))) {
            return false;
        }

        try {
            // in case call to interrupt throws exception(万一中断调用引发异常)
            if (mayInterruptIfRunning) {//若为true，表示要发送一个中断信号
                try {
                    //执行当前 FutureTask 的线程，有可能现在是null，是null 的情况是： 当前任务在 队列中，还没有线程获取到它呢。。
                    Thread t = runner;
                    //条件成立：说明当前线程 runner ，正在执行task.
                    if (t != null) {
                        //给runner线程一个中断信号.. 如果你的程序是响应中断的
                        //会走中断逻辑..假设你程序不是响应中断的..啥也不会发生。
                        t.interrupt();
                    }
                } finally { // final state
                    //设置任务状态为 中断完成。
                    UNSAFE.putOrderedInt(this, stateOffset, INTERRUPTED);
                }
            }
        } finally {
            //唤醒所有get() 阻塞的线程。
            finishCompletion();
        }
        return true;
    }

    /**
     * @throws CancellationException {@inheritDoc}
     *
     * 可能会有多个线程在 get() 都需要阻塞
     *
     * 场景：多个线程等待当前任务执行完成后的结果...
     */
    public V get() throws InterruptedException, ExecutionException {
        //获取当前任务状态
        int s = state;
        //条件成立：未执行、正在执行、正完成。 调用get的外部线程会被阻塞在get方法上。
        if (s <= COMPLETING) {//只有 NEW 或者 COMPLETING
            //返回task当前状态，可能当前线程在里面已经睡了一会了..
            s = awaitDone(false, 0L);
        }
        return report(s);
    }

    /**
     * @throws CancellationException {@inheritDoc}
     */
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (unit == null) {
            throw new NullPointerException();
        }
        int s = state;
        if (s <= COMPLETING
                &&
                (s = awaitDone(true, unit.toNanos(timeout))) <= COMPLETING) {
            throw new TimeoutException();
        }
        return report(s);
    }

    /**
     * Protected method invoked when this task transitions to state
     * {@code isDone} (whether normally or via cancellation). The
     * default implementation does nothing.  Subclasses may override
     * this method to invoke completion callbacks or perform
     * bookkeeping. Note that you can query status inside the
     * implementation of this method to determine whether this task
     * has been cancelled.
     */
    protected void done() {
    }

    /**
     * Sets the result of this future to the given value unless
     * this future has already been set or has been cancelled.
     *
     * <p>This method is invoked internally by the {@link #run} method
     * upon successful completion of the computation.
     *
     * @param v the value
     */
    protected void set(V v) {
        //使用CAS方式设置当前任务状态为 完成中..
        //有没有可能失败呢？ 外部线程等不及了，直接在set执行CAS之前 将  task取消了。  很小概率事件。
        //还有就是当前任务执行发生了异常，此时 setException 已经把 state 改为了 COMPLETING, 所以当前是不会执行的
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = v;
            //将结果赋值给 outcome 之后，马上会将当前任务状态修改为 NORMAL 正常结束状态。
            //其实就是写入一个 int 类型的值
            UNSAFE.putOrderedInt(this, stateOffset, NORMAL); // final state
            //猜一猜？
            //最起码得把get() 在此阻塞的线程 唤醒..
            finishCompletion();
        }
    }

    /**
     * Causes this future to report an {@link ExecutionException}
     * with the given throwable as its cause, unless this future has
     * already been set or has been cancelled.
     *
     * <p>This method is invoked internally by the {@link #run} method
     * upon failure of the computation.
     *
     * @param t the cause of failure
     */
    protected void setException(Throwable t) {
        //使用CAS方式设置当前任务状态为 完成中..
        //有没有可能失败呢？ 外部线程等不及了，直接在set执行CAS之前 将  task取消了。  很小概率事件。
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            //引用的是 callable 向上层抛出来的异常。
            outcome = t;
            //将当前任务的状态 修改为 EXCEPTIONAL
            UNSAFE.putOrderedInt(this, stateOffset, EXCEPTIONAL); // final state
            //回头讲完 get() 。
            finishCompletion();
        }
    }

    //submit(runnable/callable) -> newTaskFor(runnable) -> execute(task)   -> pool
    //任务执行入口
    public void run() {
        if (state != NEW//如果 state != NEW 成立，说明当前task已经被执行过了 或者 被cancel 了，总之非NEW状态的任务，线程就不处理了。
                ||
                //条件成立：cas失败，当前任务被其它线程抢占了
                //把当前对象中的 Thread runner 字段设置为当前线程，也就是当前线程在跟其他线程抢占该任务
                !UNSAFE.compareAndSwapObject(this, runnerOffset, null, Thread.currentThread())) {

            //run的时候状态不是 NEW 或者 当前任务已经有线程在执行了，则当前线程直接返回
            return;
        }

        //执行到这里，当前task一定是 NEW 状态，而且 当前线程也抢占TASK成功！

        try {
            //callable 就是程序员自己封装逻辑的 callable 或者 装饰后的 runnable
            //这个 callable 是程序员传进来的
            Callable<V> c = callable;
            if (c != null //c != null 防止空指针异常
                    &&
                    //虽然前面判断过了，但是可能被主线程取消了
                    //防止外部线程 cancel掉当前任务。
                    state == NEW) {

                //结果引用，如果传入的 Runnable 则特殊点
                V result;

                //true 表示callable.run 代码块执行成功 未抛出异常
                //false 表示callable.run 代码块执行失败 抛出异常
                boolean ran;

                try {
                    //调用程序员自己实现的 Callable 或者 装饰后的 Runnable
                    result = c.call();
                    //c.call未抛出任何异常，ran会设置为true 代码块执行成功
                    ran = true;
                } catch (Throwable ex) {
                    //说明程序员自己写的逻辑块有bug了。或者抛出了异常
                    result = null;
                    ran = false;
                    setException(ex);
                }

                if (ran) {
                    //说明当前c.call执行结束了。
                    //set就是设置结果到outcome
                    set(result);
                }
            }
        } finally {
            // runner must be non-null until state is settled to
            // prevent concurrent calls to run()
            runner = null;
            // state must be re-read after nulling runner to prevent
            // leaked interrupts
            int s = state;
            if (s >= INTERRUPTING) {
                //回头再说..讲了 cancel() 就明白了。
                handlePossibleCancellationInterrupt(s);
            }
        }
    }

    /**
     * Executes the computation without setting its result, and then
     * resets this future to initial state, failing to do so if the
     * computation encounters an exception or is cancelled.  This is
     * designed for use with tasks that intrinsically execute more
     * than once.
     *
     * 在不设置计算结果的情况下执行计算，然后将此未来状态重置为初始状态，如果计算遇到异常或被取消，则无法执行此操作。设计用于本质上执行多次的任务。
     *
     * @return {@code true} if successfully run and reset
     */
    protected boolean runAndReset() {
        if (state != NEW
                ||
                !UNSAFE.compareAndSwapObject(this, runnerOffset, null, Thread.currentThread())) {
            return false;
        }
        boolean ran = false;
        int s = state;
        try {
            Callable<V> c = callable;
            if (c != null && s == NEW) {
                try {
                    c.call(); // don't set result
                    ran = true;
                } catch (Throwable ex) {
                    setException(ex);
                }
            }
        } finally {
            // runner must be non-null until state is settled to
            // prevent concurrent calls to run()
            runner = null;
            // state must be re-read after nulling runner to prevent
            // leaked interrupts
            s = state;
            if (s >= INTERRUPTING) {
                handlePossibleCancellationInterrupt(s);
            }
        }
        return ran && s == NEW;
    }

    /**
     * Ensures that any interrupt from a possible cancel(true) is only
     * delivered to a task while in run or runAndReset.
     */
    private void handlePossibleCancellationInterrupt(int s) {
        // It is possible for our interrupter to stall before getting a
        // chance to interrupt us.  Let's spin-wait patiently.
        if (s == INTERRUPTING) {
            while (state == INTERRUPTING) {
                Thread.yield(); // wait out pending interrupt
            }
        }

        // assert state == INTERRUPTED;

        // We want to clear any interrupt we may have received from
        // cancel(true).  However, it is permissible to use interrupts
        // as an independent mechanism for a task to communicate with
        // its caller, and there is no way to clear only the
        // cancellation interrupt.
        //
        // Thread.interrupted();
    }

    /**
     * Simple linked list nodes to record waiting threads in a Treiber
     * stack.  See other classes such as Phaser and SynchronousQueue
     * for more detailed explanation.
     *
     * 这是一个栈
     * 表示有多个线程调用 FutureTask#get()函数 等待
     *
     * waiting 状态的线程是不会去抢占cpu的,除非被唤醒
     */
    static final class WaitNode {

        volatile Thread thread;

        volatile WaitNode next;

        WaitNode() {
            thread = Thread.currentThread();
        }
    }

    /**
     * Removes and signals all waiting threads, invokes done(), and
     * nulls out callable.
     */
    private void finishCompletion() {
        // assert state > COMPLETING;
        //q指向waiters 链表的头结点。
        for (WaitNode q; (q = waiters) != null; ) {

            //使用cas设置 waiters 为 null 是因为怕 外部线程使用 cancel 取消当前任务 也会触发finishCompletion方法。 小概率事件。
            if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) {
                for (; ; ) {
                    //获取当前node节点封装的 thread
                    Thread t = q.thread;
                    //条件成立：说明当前线程不为null
                    if (t != null) {

                        q.thread = null;//help GC
                        //唤醒当前节点对应的线程

                        /**
                         * @see FutureTask#awaitDone(boolean, long)
                         */
                        LockSupport.unpark(t);
                    }

                    //next 当前节点的下一个节点
                    WaitNode next = q.next;

                    if (next == null) {
                        break;
                    }

                    q.next = null; // unlink to help gc
                    q = next;
                }

                break;
            }
        }

        done();

        //将callable 设置为null helpGC
        callable = null;        // to reduce footprint
    }

    /**
     * Awaits completion or aborts on interrupt or timeout.
     *
     * ---
     *
     * 等待完成，或者在中断或超时时中止。
     *
     * @param timed true if use timed waits
     * @param nanos time to wait, if timed
     * @return state upon completion
     */
    private int awaitDone(boolean timed, long nanos) throws InterruptedException {
        //0 不带超时
        final long deadline = timed ? System.nanoTime() + nanos : 0L;
        //引用当前线程 封装成 WaitNode 对象
        WaitNode q = null;
        //表示当前线程 waitNode 对象 有没有 入栈
        boolean queued = false;
        //自旋
        for (; ; ) {

            //条件成立：说明当前线程唤醒 是被其它线程使用中断这种方式喊醒的。
            //interrupted() 返回true 后会将 Thread的中断标记重置回false.
            if (Thread.interrupted()) {
                //当前线程node出队
                removeWaiter(q);
                //get方法抛出 中断异常。
                throw new InterruptedException();
            }

            //假设当前线程是被其它线程 使用unpark(thread) 唤醒的话。会正常自旋，走下面逻辑。

            //获取当前任务最新状态
            int s = state;

            //条件成立：说明当前任务 已经有结果了.. 可能是好 可能是 坏..
            if (s > COMPLETING) {
                //条件成立：说明已经为当前线程创建过node了，此时需要将 node.thread = null helpGC
                if (q != null) {
                    //TODO ？？？
                    q.thread = null;
                }
                //直接返回当前状态.
                return s;
            }

            //条件成立：说明当前任务接近完成状态...这里让当前线程再释放cpu ，进行下一次抢占cpu。
            else if (s == COMPLETING) {
                // cannot time out yet
                Thread.yield();
            } else if (q == null) {
                //条件成立：第一次自旋，当前线程还未创建 WaitNode 对象，此时为当前线程创建 WaitNode对象
                q = new WaitNode();
            }

            //条件成立：第二次自旋，当前线程已经创建 WaitNode对象了，但是node对象还未入队
            else if (!queued) {
                //当前线程node节点 next 指向 原 队列的头节点   waiters 一直指向队列的头！
                q.next = waiters;
                //cas方式设置waiters引用指向 当前线程node， 成功的话 queued == true 否则，可能其它线程先你一步入队了。
                queued = UNSAFE.compareAndSwapObject(this, waitersOffset, waiters, q);
            }

            //第三次自旋，会到这里。
            else if (timed) {
                nanos = deadline - System.nanoTime();
                if (nanos <= 0L) {
                    removeWaiter(q);
                    return state;
                }
                LockSupport.parkNanos(this, nanos);
            }

            //如果 timed = false
            else {
                //当前get操作的线程就会被park了。  线程状态会变为 WAITING状态，相当于休眠了..
                //除非有其它线程将你唤醒  或者 将当前线程 中断。
                //TODO 当前线程在此休眠，下次唤醒的时候还是从这里往后执行代码
                LockSupport.park(this);
            }
        }
    }

    /**
     * Tries to unlink a timed-out or interrupted wait node to avoid
     * accumulating garbage.  Internal nodes are simply unspliced
     * without CAS since it is harmless if they are traversed anyway
     * by releasers.  To avoid effects of unsplicing from already
     * removed nodes, the list is retraversed in case of an apparent
     * race.  This is slow when there are a lot of nodes, but we don't
     * expect lists to be long enough to outweigh higher-overhead
     * schemes.
     *
     * 尝试取消链接超时或中断的等待节点，以避免积累垃圾。内部节点在没有CAS的情况下根本不会被拼接，因为如果释放者无论如何遍历它们，都是无害的。
     * 为了避免从已删除的节点取消拆分的影响，在出现明显竞争的情况下会重新遍历该列表。当节点很多时，这很慢，但是我们不希望列表足够长以超过开销更高的方案。
     *
     * 从栈中移除节点 node
     *
     * a -> b -> c -> d -> e
     * *
     * 该方法做的事情是：
     * 1.给定一个要删除的节点 node
     * 2.删除给定的节点node
     * 3.删除 waiters 栈中所有的 thread 为null的节点
     * 4.并且重新赋值 waiters
     * TODO 但是具体的实现没有看明白！！！！！
     */
    private void removeWaiter(WaitNode node) {
        if (node != null) {

            //先把节点的 thread 属性设置为 null
            node.thread = null;
            retry:
            for (; ; ) {// restart on removeWaiter race 重新启动removeWaiter竞赛
                for (
                    //当前节点的上一个
                        WaitNode pred = null,
                        //当前节点
                        q = waiters,
                        //s = q.next;当前节点的下一个节点
                        s;
                    //循环结束条件，当前节点不为null
                        q != null;
                    //下次循环的起点，把当前节点指向下一个节点
                        q = s) {

                    //赋值为当前节点的下一个节点
                    s = q.next;
                    if (q.thread != null) {
                        pred = q;
                    } else if (pred != null) {
                        pred.next = s;
                        if (pred.thread == null) {
                            // check for race
                            continue retry;
                        }
                    } else if (!UNSAFE.compareAndSwapObject(this, waitersOffset, q, s)) {
                        continue retry;
                    }
                }
                break;
            }
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;

    private static final long stateOffset;

    private static final long runnerOffset;

    private static final long waitersOffset;

    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = FutureTask.class;
            stateOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("state"));
            runnerOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("runner"));
            waitersOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("waiters"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
