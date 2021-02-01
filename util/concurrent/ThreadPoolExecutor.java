package util.concurrent;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An {@link ExecutorService} that executes each submitted task using one of possibly several pooled threads, normally configured using {@link Executors} factory methods.
 * -- 一个{@link ExecutorService}，它可以使用多个池线程中的一个执行每个提交的任务，通常使用{@link Executors}工厂方法进行配置。
 *
 * Thread pools address two different problems:
 * -- 线程池解决了两个不同的问题：
 *
 * they usually provide improved performance when executing large numbers of asynchronous tasks,
 * -- 当执行大量异步任务时，它们通常可以提高性能，
 *
 * due to reduced per-task invocation overhead, and they provide a means of bounding and managing the resources, including threads, consumed when executing a collection of tasks.
 * -- 由于减少了每个任务的调用开销，因此它们提供了一种绑定和管理在执行任务集合时消耗的资源（包括线程）的方法。
 *
 * Each {@code ThreadPoolExecutor} also maintains some basic statistics, such as the number of completed tasks.
 * -- 每个{@code ThreadPoolExecutor}还维护一些基本统计信息，例如已完成任务的数量。
 *
 * To be useful across a wide range of contexts, this class provides many adjustable parameters and extensibility hooks.
 * -- 为了在广泛的上下文中有用，该类提供了许多可调整的参数和可扩展性挂钩。(意思就是他有很多的钩子函数，感觉自己很吊)
 *
 * However, programmers are urged to use the more convenient {@link Executors} factory methods {@link Executors#newCachedThreadPool} (unbounded thread pool, with automatic thread reclamation), {@link Executors#newFixedThreadPool}(fixed size thread pool) and {@link Executors#newSingleThreadExecutor} (single background thread), that preconfigure settings for the most common usage scenarios.
 * - 但是，强烈建议程序员使用更方便的{@link Executors}工厂方法{@link Executors＃newCachedThreadPool}（无边界线程池，具有自动线程回收），{@link Executors＃newFixedThreadPool}（固定大小的线程池）和{ @link Executors＃newSingleThreadExecutor}（单个后台线程），可以为最常见的使用场景预配置设置。
 *
 * Otherwise, use the following guide when manually configuring and tuning this class:
 * -- 否则，在手动配置和调整此类时，请使用以下指南：
 *
 * <dl>
 *
 * <dt>Core and maximum pool sizes</dt>
 *
 * A {@code ThreadPoolExecutor} will automatically adjust the pool size (see {@link #getPoolSize}) according to the bounds set by corePoolSize (see {@link #getCorePoolSize}) and maximumPoolSize (see {@link #getMaximumPoolSize}).
 *
 * When a new task is submitted in method {@link #execute(Runnable)}, and fewer than corePoolSize threads are running, a new thread is created to handle the request, even if other worker threads are idle.
 * -- 当在方法{@link #execute（Runnable）}中提交新任务，并且正在运行的线程少于corePoolSize个线程时，即使其他工作线程处于空闲状态，也会创建一个新线程来处理请求。
 *
 * If there are more than corePoolSize but less than maximumPoolSize threads running, a new thread will be created only if the queue is full.
 * -- 如果运行的线程数大于corePoolSize但小于maximumPoolSize，则仅在队列已满时才创建新线程。
 *
 * By setting corePoolSize and maximumPoolSize
 * the same, you create a fixed-size thread pool. By setting
 * maximumPoolSize to an essentially unbounded value such as {@code
 * Integer.MAX_VALUE}, you allow the pool to accommodate an arbitrary
 * number of concurrent tasks. Most typically, core and maximum pool
 * sizes are set only upon construction, but they may also be changed
 * dynamically using {@link #setCorePoolSize} and {@link
 * #setMaximumPoolSize}. </dd>
 *
 * -- 意思就是通过指定corePoolSize跟maximumPoolSize为不同的值就可以直线不同策略的线程池了，并且一般这2个参数都是通过构造器指定，但是也支持动态的改变l
 *
 * <dt>On-demand construction</dt>
 * -- 按需施工
 *
 * <dd>
 *     By default, even core threads are initially created and started only when new tasks arrive, but this can be overridden dynamically using method {@link #prestartCoreThread} or {@link #prestartAllCoreThreads}.
 *     -- 默认情况下，只有在有新任务到达时才开始启动核心线程，但是可以使用方法{@link #prestartCoreThread}或{@link #prestartAllCoreThreads}动态地覆盖它。
 *     You probably want to prestart threads if you construct the pool with a non-empty queue.
 *     -- 如果使用非空队列构造池，则可能要预启动线程。
 * </dd>
 *
 * <dt>Creating new threads</dt>
 *
 * <dd>
 *     New threads are created using a {@link ThreadFactory}.
 *     -- 使用{@link ThreadFactory}创建新线程。
 *
 *     If not otherwise specified, a {@link Executors#defaultThreadFactory} is used, that creates threads to all be in the same {@link ThreadGroup} and with the same {@code NORM_PRIORITY} priority and non-daemon status.
 *     -- 如果没有另外指定，则使用{@link Executors＃defaultThreadFactory}，该线程工厂创建的线程全部位于相同的{@link ThreadGroup}中，并且具有相同的{@code NORM_PRIORITY}优先级和非守护进程状态。
 *
 *     By supplying a different ThreadFactory, you can alter the thread's name, thread group, priority, daemon status, etc.
 *     -- 通过提供其他ThreadFactory，可以更改线程的名称，线程组，优先级，守护程序状态等。
 *
 *     If a {@code ThreadFactory} fails to create a thread when aske by returning null from {@code newThread}, the executor will continue, but might not be able to execute any tasks.
 *     -- 如果{@code ThreadFactory}在{@code newThread}中返回null时，在创建aske时未能创建线程，则执行程序将继续执行，但可能无法执行任何任务。
 *
 *     Threads should possess the "modifyThread" {@code RuntimePermission}.
 *
 *     If worker threads or other threads using the pool do not possess this permission, service may be degraded:
 *     -- 如果使用该池的工作线程或其他线程不具有此权限，则服务可能降级：
 *
 *     configuration changes may not take effect in a timely manner, and a shutdown pool may remain in a state in which termination is possible but not completed.
 * </dd>
 *
 * <dt>Keep-alive times</dt>
 *
 * <dd>
 *     If the pool currently has more than corePoolSize threads, excess threads will be terminated if they have been idle for more than the keepAliveTime (see {@link #getKeepAliveTime(TimeUnit)}).
 *     -- 如果当前池中的线程数超过corePoolSize线程，则多余的线程将在空闲时间超过keepAliveTime时终止（请参阅{@link #getKeepAliveTime（TimeUnit）}）。
 *
 *      This provides a means of reducing resource consumption when the pool is not being actively used.
 *      -- 当线程池不活跃的时候，这提供了减少资源消耗的方法。
 *
 *      If the pool becomes more active later, new threads will be constructed.
 *      -- 如果池稍后变得更加活跃，则将构建新线程。
 *
 *      This parameter can also be changed dynamically using method {@link #setKeepAliveTime(long, TimeUnit)}.
 *      -- 也可以使用方法{@link #setKeepAliveTime（long，TimeUnit）}动态更改此参数。
 *
 *      Using a value of {@code Long.MAX_VALUE} {@link TimeUnit#NANOSECONDS} effectively disables idle threads from ever terminating prior to shut down.
 *      -- 使用{@code Long.MAX_VALUE}的值{@link TimeUnit＃NANOSECONDS}有效地使空闲线程永远不会在关闭之前终止。
 *
 *      By default, the keep-alive policy applies only when there are more than corePoolSize threads.
 *      -- 默认情况下，仅当存在多于corePoolSize个线程时，保持活动策略才适用。
 *
 *      But method {@link #allowCoreThreadTimeOut(boolean)} can be used to apply this time-out policy to core threads as well, so long as the keepAliveTime value is non-zero.
 *      -- 但是方法{@link #allowCoreThreadTimeOut（boolean）}也可以用于将此超时策略应用于核心线程，只要keepAliveTime值不为零即可。
 * </dd>
 *
 * <dt>Queuing</dt>
 *
 * <dd>
 *     Any {@link BlockingQueue} may be used to transfer and hold submitted tasks.  The use of this queue interacts with pool sizing:
 *     -- 任何{@link BlockingQueue}均可用于传输和保留提交的任务。此队列的使用与池大小交互：
 * <ul>
 *
 * <li>
 *     If fewer than corePoolSize threads are running, the Executor always prefers adding a new thread rather than queuing.
 *     -- 如果运行的线程数少于corePoolSize，则执行程序总是喜欢添加新线程，而不是排队。
 * </li>
 *
 * <li>
 *     If corePoolSize or more threads are running, the Executor always prefers queuing a request rather than adding a new thread.
 *     -- 如果正在运行的线程等于或者多于corePoolSize，则执行程序总是更喜欢对请求进行排队，而不是添加新线程。
 * </li>
 *
 * <li>
 *     If a request cannot be queued, a new thread is created unless this would exceed maximumPoolSize, in which case, the task will be rejected.
 *     -- 如果无法将请求放入队列中，则将创建一个新线程，除非该线程超过了maximumPoolSize，在这种情况下，该任务将被拒绝。
 * </li>
 *
 * </ul>
 *
 * There are three general strategies for queuing:
 * -- 有三种一般的排队策略：
 * <ol>
 *
 * <li>
 *     <em> Direct handoffs.</em> -- 直接交接
 *
 *     A good default choice for a work queue is a {@link SynchronousQueue} that hands off tasks to threads without otherwise holding them.
 *     -- {@link SynchronousQueue}是工作队列的一个很好的默认选择，它可以将任务移交给线程，而不必另外保留它们。
 *
 *     Here, an attempt to queue a task will fail if no threads are immediately available to run it, so a new thread will be constructed.
 *     -- 在这里，如果没有立即可用的线程来运行任务，则尝试将任务排队将失败，因此将构造一个新线程。
 *
 *     This policy avoids lockups when handling sets of requests that might have internal dependencies.'
 *     -- 在处理可能具有内部依赖项的请求集时，此策略避免了锁定。
 *
 *     Direct handoffs generally require unbounded maximumPoolSizes to avoid rejection of new submitted tasks.
 *     -- 直接交接通常需要无限制的maximumPoolSizes，以避免拒绝新提交的任务。
 *
 *     This in turn admits the possibility of unbounded thread growth when commands continue to arrive on average faster than they can be processed.
 *     -- 反过来，当平均而言，命令继续以比其处理速度更快的速度到达时，就可以实现无限线程增长的可能性。
 * </li>
 *
 * <li>
 *     <em> Unbounded queues.</em> -- 无限队列
 *
 *     Using an unbounded queue (for example a {@link LinkedBlockingQueue} without a predefined capacity) will cause new tasks to wait in the queue when all corePoolSize threads are busy.
 *     -- 当所有corePoolSize线程都忙时，使用无限制的队列（例如，没有预定义容量的{@link LinkedBlockingQueue}）将导致新任务在队列中等待。
 *
 *     Thus, no more than corePoolSize threads will ever be created. (And the value of the maximumPoolSize therefore doesn't have any effect.)
 *     -- 因此，将仅创建corePoolSize线程。 （因此，maximumPoolSize的值没有任何作用。）
 *
 *     This may be appropriate when each task is completely independent of others, so tasks cannot affect each others execution; for example, in a web page server.
 *     -- 当每个任务完全独立于其他任务时，这可能是适当的，因此任务不会影响彼此的执行。例如，在网页服务器中。
 *
 *     While this style of queuing can be useful in smoothing out transient bursts of requests,
 *     it admits the possibility of unbounded work queue growth when commands continue to arrive on average faster than they can be processed.
 *     -- 尽管这种排队方式对于消除短暂的请求突发很有用，它承认当命令平均到达的速度比处理命令的速度快时，工作队列可能会无限增长。
 * </li>
 *
 * <li>
 *     <em>Bounded queues.</em> -- 有界队列
 *
 *     A bounded queue (for example, an{@link ArrayBlockingQueue}) helps prevent resource exhaustion when used with finite maximumPoolSizes, but can be more difficult to tune and control.
 *     -- 当与有限的maximumPoolSizes一起使用时，有界队列（例如， link ArrayBlockingQueue}）有助于防止资源耗尽，但是调优和控制起来会更加困难。
 *
 *     Queue sizes and maximum pool sizes may be traded off for each other:
 *     -- 队列大小和最大池大小可能会相互折衷
 *
 *     Using large queues and small pools minimizes CPU usage, OS resources, and context-switching overhead, but can lead to artificially low throughput.
 *     -- 使用大队列和小池可最大程度地减少CPU使用率，操作系统资源和上下文切换开销，但可能导致人为地降低吞吐量。
 *
 *     If tasks frequently block (for example if they are I/O bound),a system may be able to schedule time for more threads than you otherwise allow.
 *     -- 如果任务频繁阻塞（例如，如果它们受I / O约束），则系统可能能够安排比您原先允许的线程更多的时间。
 *
 *     Use of small queues generally requires larger pool sizes, which keeps CPUs busier but may encounter unacceptable scheduling overhead, which also decreases throughput.
 *     -- 使用小队列通常需要更大的池大小，这会使CPU繁忙，但可能会遇到不可接受的调度开销，这也会降低吞吐量。
 * </li>
 *
 * </ol>
 *
 * </dd>
 *
 * <dt>Rejected tasks</dt>
 *
 * <dd>
 *     New tasks submitted in method {@link #execute(Runnable)} will be <em>rejected</em> when the Executor has been shut down, and also when the Executor uses finite bounds for both maximum threads and work queue capacity, and is saturated.
 *     -- 当执行器关闭时(shutdown)，并且执行器对最大线程数和工作队列容量使用有限范围时，在方法{@link #execute（Runnable）}中提交的新任务将被<em>拒绝</ em>。已饱和。
 *
 *     In either case, the {@code execute} method invokes the {@link RejectedExecutionHandler#rejectedExecution(Runnable, ThreadPoolExecutor)} method of its {@link RejectedExecutionHandler}.
 *
 *     Four predefined handler policies are provided:
 *
 * <ol>
 *
 * <li> In the default {@link AbortPolicy}, the
 * handler throws a runtime {@link RejectedExecutionException} upon
 * rejection. </li>
 *
 * <li> In {@link CallerRunsPolicy}, the thread
 * that invokes {@code execute} itself runs the task. This provides a
 * simple feedback control mechanism that will slow down the rate that
 * new tasks are submitted. </li>
 *
 * <li> In {@link DiscardPolicy}, a task that
 * cannot be executed is simply dropped.  </li>
 *
 * <li>In {@link DiscardOldestPolicy}, if the
 * executor is not shut down, the task at the head of the work queue
 * is dropped, and then execution is retried (which can fail again,
 * causing this to be repeated.) </li>
 *
 * </ol>
 *
 * It is possible to define and use other kinds of {@link
 * RejectedExecutionHandler} classes. Doing so requires some care
 * especially when policies are designed to work only under particular
 * capacity or queuing policies.
 *
 * </dd>
 *
 * <dt>Hook methods</dt> -- 钩子方法
 *
 * <dd>
 *
 *     This class provides {@code protected} overridable {@link #beforeExecute(Thread, Runnable)} and {@link #afterExecute(Runnable, Throwable)} methods that are called before and after execution of each task.
 *     -- 此类提供{@code protected}可重写的{@link #beforeExecute（Thread，Runnable）}和{@link #afterExecute（Runnable，Throwable）}方法，这些方法在每个任务执行前后被调用。
 *
 *     These can be used to manipulate the execution environment; for example, reinitializing ThreadLocals, gathering statistics, or adding log entries.
 *     -- 这些可以用来操纵执行环境。例如，重新初始化ThreadLocals，收集统计信息或添加日志条目。
 *
 *     Additionally, method {@link #terminated} can be overridden to perform any special processing that needs to be done once the Executor has fully terminated.
 *     -- 另外，一旦执行程序完全终止，方法{@link #terminated}可以被覆盖以执行需要执行的任何特殊处理。
 *
 *     If hook or callback methods throw exceptions, internal worker threads may in turn fail and abruptly terminate.
 *     -- 如果钩子或回调方法引发异常，内部工作线程可能进而失败并突然终止。
 *
 * </dd>
 *
 * <dt>Queue maintenance</dt> -- 队列维
 *
 * <dd>
 *
 *     Method {@link #getQueue()} allows access to the work queue for purposes of monitoring and debugging.
 *     -- 方法{@link #getQueue（）}允许访问工作队列，以进行监视和调试。
 *
 *     Use of this method for any other purpose is strongly discouraged.
 *     -- 强烈建议不要将此方法用于任何其他目的。
 *
 *     Two supplied methods, {@link #remove(Runnable)} and {@link #purge} are available to assist in storage reclamation when large numbers of queued tasks become cancelled.
 *     -- 当取消大量排队的任务时，可以使用提供的两种方法{@link #remove（Runnable）}和{@link #purge}来帮助回收存储。
 *
 * </dd>
 *
 * <dt>Finalization</dt>
 *
 * <dd>
 *
 *     A pool that is no longer referenced in a program <em>AND</em> has no remaining threads will be {@code shutdown} automatically.
 *     -- 程序<em> AND </ em>中不再引用的没有剩余线程的池将自动{@code shutdown}。
 *
 *     If you would like to ensure that unreferenced pools are reclaimed even if users forget to call {@link #shutdown}, then you must arrange that unused threads eventually die, by setting appropriate keep-alive times, using a lower bound of zero core threads and/or setting {@link #allowCoreThreadTimeOut(boolean)}.
 *     -- 如果即使在用户忘记调用{@link #shutdown}的情况下，如果您想确保回收未引用的池，则必须使用零核心线程的下限来设置适当的保持活动时间，以使未使用的线程最终消亡。和/或设置{@link #allowCoreThreadTimeOut（boolean）}。
 *
 * </dd>
 *
 * </dl>
 *
 * <p><b>Extension example</b>. Most extensions of this class override one or more of the protected hook methods. For example,here is a subclass that adds a simple pause/resume feature:
 * -- 这是一个添加简单暂停/恢复功能的子类：
 *
 *  <pre> {@code
 * class PausableThreadPoolExecutor extends ThreadPoolExecutor {
 *   private boolean isPaused;
 *   private ReentrantLock pauseLock = new ReentrantLock();
 *   private Condition unpaused = pauseLock.newCondition();
 *
 *   public PausableThreadPoolExecutor(...) { super(...); }
 *
 *   protected void beforeExecute(Thread t, Runnable r) {
 *     super.beforeExecute(t, r);
 *     pauseLock.lock();
 *     try {
 *       while (isPaused) unpaused.await();
 *     } catch (InterruptedException ie) {
 *       t.interrupt();
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 *
 *   public void pause() {
 *     pauseLock.lock();
 *     try {
 *       isPaused = true;
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 *
 *   public void resume() {
 *     pauseLock.lock();
 *     try {
 *       isPaused = false;
 *       unpaused.signalAll();
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 * }}</pre>
 *
 * @author Doug Lea
 * @since 1.5
 */
@SuppressWarnings("all")
public class ThreadPoolExecutor extends AbstractExecutorService {

    /**
     * The main pool control state, ctl, is an atomic integer packing two conceptual fields workerCount, indicating the effective number of threads runState,indicating whether running, shutting down etc
     *
     * 主池控制状态ctl是一个原子整数，它包装了两个概念字段workerCount，指示线程的有效数量runState，指示是否运行，关闭等
     *
     * runState + workerCount = ctl
     *
     * In order to pack them into one int, we limit workerCount to
     * (2^29)-1 (about 500 million) threads rather than (2^31)-1 (2
     * billion) otherwise representable. If this is ever an issue in
     * the future, the variable can be changed to be an AtomicLong,
     * and the shift/mask constants below adjusted. But until the need
     * arises, this code is a bit faster and simpler using an int.
     *
     * The workerCount is the number of workers that have been permitted to start and not permitted to stop.
     * -- workerCount是已被允许启动但不允许停止的工人数。
     *
     * The value may be transiently different from the actual number of live threads,for example when a ThreadFactory fails to create a thread when asked, and when exiting threads are still performing bookkeeping before terminating.
     * -- 该值可能与活动线程的实际数量有短暂的差异，例如，当ThreadFactory在被询问时未能创建线程，并且退出线程仍在终止之前执行簿记操作时，该值会有所不同。
     *
     * The user-visible pool size is reported as the current size of the workers set.
     * -- 用户可见的池大小报告为工作集的当前大小。
     *
     * The runState provides the main lifecycle control, taking on values:
     * -- runState提供主要的生命周期控制，并具有以下值：
     *
     * RUNNING:  Accept new tasks and process queued tasks --- 接受新任务并处理排队的任务
     * SHUTDOWN: Don't accept new tasks, but process queued tasks --- 不接受新任务，但是处理排队的任务
     * STOP:     Don't accept new tasks, don't process queued tasks, and interrupt in-progress tasks --- 不接受新任务，不处理排队任务以及中断进行中的任务
     * TIDYING:  All tasks have terminated, workerCount is zero, the thread transitioning to state TIDYING will run the terminated() hook method
     * --- 所有任务已终止，workerCount为零，转换到状态TIDYING的线程将运行terminated()挂钩方法
     * TERMINATED: terminated() has completed --- terminated()方法结束就是该状态
     *
     * The numerical order among these values matters, to allow ordered comparisons. The runState monotonically increases over time, but need not hit each state. The transitions are:
     * -- 这些值之间的数字顺序很重要，可以进行有序的比较。 runState随时间单调增加，但不必达到每个状态。过渡是：
     *
     * RUNNING -> SHUTDOWN On invocation of shutdown(), perhaps implicitly in finalize() --- 在调用shutdown（）时，可能隐式在finalize（）中
     * (RUNNING or SHUTDOWN) -> STOP On invocation of shutdownNow() --- 在调用shutdownNow（）时
     * SHUTDOWN -> TIDYING When both queue and pool are empty --- 当队列和池都为空时
     * STOP -> TIDYING When pool is empty --- 当池为空时
     * TIDYING -> TERMINATED When the terminated() hook method has completed --- 当Terminate（）挂钩方法完成时
     *
     * Threads waiting in awaitTermination() will return when the state reaches TERMINATED.
     * --- 状态达到TERMINATED时，在awaitTermination（）中等待的线程将返回。
     *
     * Detecting the transition from SHUTDOWN to TIDYING is less straightforward than you'd like because the queue may become empty after non-empty and vice versa during SHUTDOWN state,
     * --- 检测从SHUTDOWN到TIDYING的转换并不像您想要的那样简单，因为在SHUTDOWN状态期间，队列在非空之后可能变空，反之亦然，
     * but we can only terminate if, after seeing that it is empty, we see that workerCount is 0 (which sometimes entails a recheck -- see below).
     * -- 但是只有在看到它为空之后，我们看到workerCount为0（这有时需要重新检查-见下文），我们才能终止。
     *
     * 高3位：表示当前线程池运行状态   除去高3位之后的低位（低29位）：表示当前线程池中所拥有的线程数量
     *
     * 初始化ctl为：
     *
     * 111 0 0000 0000 0000 0000 0000 0000 0000
     * 000 0 0000 0000 0000 0000 0000 0000 0000
     * 二者进行或运算得到：
     * 111 0 0000 0000 0000 0000 0000 0000 0000
     *
     * 表示状态为RUNNING,当前线程数量为0
     */
    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));

    /**
     * 表示在ctl中，低 COUNT_BITS 位 是用于存放当前线程数量的位。
     * Integer.SIZE = 32
     * 计线程数的bits，一般是29个bit
     */
    private static final int COUNT_BITS = Integer.SIZE - 3;

    /**
     * 低COUNT_BITS(29)位 所能表达的最大数值。 000 1 1111 1111 1111 111 1111 => 5亿多。
     */
    private static final int CAPACITY = (1 << COUNT_BITS) - 1;

    // runState is stored in the high-order bits -- runState存储在高位

    /**
     * 111 000000000000000000  转换成整数，其实是一个负数
     *
     * -1   的二进制：111 1 1111 1111 1111 1111 1111 1111 1111
     * 左移29位为   ：111 0 0000 0000 0000 0000 0000 0000 0000
     *
     * 表示为十进制  ：-786432
     */
    private static final int RUNNING = -1 << COUNT_BITS;

    /**
     * 0 的二进制：000 0 0000 0000 0000 0000 0000 0000 0000
     * 左移29位为   ：000 0 0000 0000 0000 0000 0000 0000 0000
     *
     * 表示为十进制：0
     */
    private static final int SHUTDOWN = 0 << COUNT_BITS;

    /**
     * 001 000000000000000000
     *
     * 1的二进制：   000 0 0000 0000 0000 0000 0000 0000 0001
     * 左移29位为：  001 0 0000 0000 0000 0000 0000 0000 0000
     *
     * 表示为十进制：262144
     */
    private static final int STOP = 1 << COUNT_BITS;

    /**
     * 010 000000000000000000
     * 当最后一个线程退出的时候，会进入整理状态
     *
     * 2的二进制：   000 0 0000 0000 0000 0000 0000 0000 0010
     * 左移29位为：  010 0 0000 0000 0000 0000 0000 0000 0000
     *
     * 十进制：     524288
     */
    private static final int TIDYING = 2 << COUNT_BITS;

    /**
     * 011 000000000000000000
     *
     * 3的二进制：   000 0 0000 0000 0000 0000 0000 0000 0011
     * 左移29位为：  011 0 0000 0000 0000 0000 0000 0000 0000
     *
     * 十进制：     786432
     */
    private static final int TERMINATED = 3 << COUNT_BITS;

    // Packing and unpacking ctl
    //包装和开箱CTL：有些方法是拆开ctl，把他拆成高3为用来得到状态，或者低29位用来得到线程数量
    //有的时候把高三位跟低29位进行装箱（合并）成一个完整的ctl

    /**
     * 获取当前线程池运行状态
     *
     * 已知：
     * CAPACITY     = 000 111111111111111111111
     * ~CAPACITY    = 111 000000000000000000000
     *
     * 假设：
     * c            = 111 000000000000000000111，是RUNNING状态
     * c & ~CAPACITY= 111 000000000000000000000
     *
     * 结果分析：
     * 前三位还是ctl的前三位，但是后29位都是0了
     * 这个结果就是当前线程池的状态
     *
     * 总结：
     * 1.传入当前  c =ctl
     * 2.把 c 的低29位都设置位0
     * 3.此时整个c的32位就是表示状态
     *
     * @param c ctl
     * @return
     */
    private static int runStateOf(int c) {
        return c & ~CAPACITY;
    }

    /**
     * 获取当前线程池线程数量
     *
     * 已知：
     * CAPACITY     = 000 111111111111111111111
     *
     * 假设：
     * c            = 111 000000000000000000111，为RUNNING状态
     *
     * 则：
     * c & CAPACITY = 000 000000000000000000111 有7个线程
     *
     * 结果分析：
     * 前3位都是0了，而后29位还是原来c的后29位，
     * 因为ctl之前的低29位就是线程数量，所以得到的结果整个32位就是线程数量
     *
     * 总结：
     * 1.传入当前 c= ctl
     * 2.把 c 的高三位都设置位，低29位不变
     * 3.则结果整个32位就是当前线程数量
     *
     * @param c ctl
     * @return
     */
    private static int workerCountOf(int c) {
        return c & CAPACITY;
    }

    /**
     * 用在重置当前线程池ctl值时  会用到,
     *
     * !!!!!!!可以理解为把之前分开的ctl合并为分开之前的ctl
     *
     * 假设：
     * rs       = 111 000000000000000000  RUNNING
     * wc       = 000 000000000000000111  表示当前线程池有11个线程
     *
     * 则：
     * rs | wc  = 111 000000000000000111  重新合并之后的ctl能够表示2个属性，前3还是表示状态为 RUNNING,后29还能表示当前线程池有11个线程
     *
     * @param rs 表示线程池状态
     * @param wc 表示当前线程池中worker（线程）数量
     * @return 新的ctl值
     */
    private static int ctlOf(int rs, int wc) {
        return rs | wc;
    }

    /**
     * 不需要解压缩ctl的位字段访问器。这些取决于位布局和workerCount永远不会为负。
     *
     * 一般用于：比较当前线程池ctl所表示的状态，是否小于某个状态s
     *
     * c = 111 000000000000000111 <  000 000000000000000000 == true
     *
     * 所有情况下，RUNNING < SHUTDOWN < STOP < TIDYING < TERMINATED
     *
     * @param c
     * @param s
     * @return
     */
    private static boolean runStateLessThan(int c, int s) {
        return c < s;
    }

    //比较当前线程池ctl所表示的状态，是否大于等于某个状态s
    private static boolean runStateAtLeast(int c, int s) {
        return c >= s;
    }

    //小于SHUTDOWN 的一定是RUNNING。 SHUTDOWN == 0
    private static boolean isRunning(int c) {
        return c < SHUTDOWN;
    }

    /**
     * Attempts to CAS-increment the workerCount field of ctl.
     * 使用CAS方式 让ctl值+1 ，成功返回true, 失败返回false
     */
    private boolean compareAndIncrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect + 1);
    }

    /**
     * Attempts to CAS-decrement the workerCount field of ctl.
     * 使用CAS方式 让ctl值-1 ，成功返回true, 失败返回false
     */
    private boolean compareAndDecrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect - 1);
    }

    /**
     * 将ctl值减一，这个方法一定成功
     *
     * Decrements the workerCount field of ctl. This is called only on
     * abrupt termination of a thread (see processWorkerExit). Other
     * decrements are performed within getTask.
     *
     * 减少ctl的workerCount字段。仅在线程突然终止时调用此方法（请参阅processWorkerExit）。其他减量在getTask中执行。
     */
    private void decrementWorkerCount() {
        //这里会一直重试，直到成功为止。
        do {
            //empty
        } while (!compareAndDecrementWorkerCount(ctl.get()));
    }

    /**
     * The queue used for holding tasks and handing off to worker threads.
     * --- 用于保留任务并移交给工作线程的队列。
     *
     * We do not require that workQueue.poll() returning null necessarily means that workQueue.isEmpty(),
     * --- 我们不要求workQueue.poll（）返回null必然意味着workQueue.isEmpty（），
     *
     * so rely solely on isEmpty to see if the queue is empty (which we must do for example when deciding whether to transition from SHUTDOWN to TIDYING).
     * --- 因此，仅依靠isEmpty来查看队列是否为空(例如，在决定是否从SHUTDOWN过渡到TIDYING时必须执行的操作)
     *
     * This accommodates special-purpose queues such as DelayQueues for which poll() is allowed to return null even if it may later return non-null when delays expire.
     * --- 这可容纳特殊用途的队列，例如DelayQueues，允许poll（）返回null，即使它在延迟到期后稍后可能返回non-null。
     *
     * 用于保留任务并移交给工作线程的队列。
     * 我们不要求workQueue.poll（）返回null必然意味着workQueue.isEmpty（），
     * 因此仅依靠isEmpty来查看队列是否为空（例如，在决定是否从SHUTDOWN过渡到TIDYING时必须这样做） 。
     * 这可容纳特殊用途的队列，例如DelayQueues，允许poll（）返回null，即使它在延迟到期后稍后可能返回non-null。
     *
     * 任务队列，当线程池中的线程达到核心线程数量时，再提交任务 就会直接提交到 workQueue
     * workQueue  instanceOf ArrayBrokingQueue   LinkedBrokingQueue  同步队列
     */
    private final BlockingQueue<Runnable> workQueue;

    /**
     * Lock held on access to workers set and related bookkeeping.
     * While we could use a concurrent set of some sort, it turns out
     * to be generally preferable to use a lock. Among the reasons is
     * that this serializes interruptIdleWorkers, which avoids
     * unnecessary interrupt storms, especially during shutdown.
     * Otherwise exiting threads would concurrently interrupt those
     * that have not yet interrupted. It also simplifies some of the
     * associated statistics bookkeeping of largestPoolSize etc. We
     * also hold mainLock on shutdown and shutdownNow, for the sake of
     * ensuring workers set is stable while separately checking
     * permission to interrupt and actually interrupting.
     *
     * 线程池全局锁，增加worker 减少 worker 时需要持有mainLock ， 修改线程池运行状态时，也需要。
     */
    private final ReentrantLock mainLock = new ReentrantLock();

    /**
     * Set containing all worker threads in pool. Accessed only when holding mainLock.
     * --- 集包含池中的所有工作线程。仅在持有mainLock时访问。
     *
     * 线程池中真正存放 worker->thread 的地方。
     */
    private final HashSet<Worker> workers = new HashSet<Worker>();

    /**
     * Wait condition to support awaitTermination ---- 等待条件以支持awaitTermination
     *
     * 当外部线程调用  awaitTermination() {@link ThreadPoolExecutor#awaitTermination(long, java.util.concurrent.TimeUnit)}方法时，
     * 外部线程会等待当前线程池状态为 Termination 为止。
     * 等待是如何实现的？ 就是将外部线程 封装成 waitNode 放入到 Condition 队列中了， waitNode.Thread 就是外部线程，会被park掉（处于WAITING状态）。
     * 当线程池 状态 变为 Termination时，会去唤醒这些线程。通过 termination.signalAll() ，
     * {@link ThreadPoolExecutor#tryTerminate()}唤醒之后这些线程会进入到 阻塞队列，然后头结点会去抢占mainLock。
     * 抢占到的线程，会继续执行awaitTermination() 后面程序。这些线程最后，都会正常执行。
     *
     *
     * 简单理解：
     * termination.await() 会将线程阻塞在这。
     * termination.signalAll() 会将阻塞在这的线程依次唤醒
     *
     * @see ThreadPoolExecutor#awaitTermination(long, java.util.concurrent.TimeUnit) 此方法会 termination.awaitNanos(nanos);
     * @see ThreadPoolExecutor#tryTerminate() 该方法会 termination.signalAll();
     */
    private final Condition termination = mainLock.newCondition();

    /**
     * Tracks largest attained pool size. Accessed only under mainLock.--- 跟踪达到的最大池大小。仅在mainLock下访问。
     * 记录线程池生命周期内 线程数最大值
     */
    private int largestPoolSize;

    /**
     * Counter for completed tasks. Updated only on termination of worker threads. Accessed only under mainLock.
     * --- 计数器完成的任务。仅在终止工作线程时更新。仅在mainLock下访问
     *
     * 记录线程池所完成任务总数 ，当worker退出时会将 worker完成的任务累积到completedTaskCount
     */
    private long completedTaskCount;

    /*
     * All user control parameters are declared as volatiles so that ongoing actions are based on freshest values,
     * --- 所有用户控制参数都声明为volatile，以便正在进行的操作基于最新值
     *
     * but without need for locking, since no internal invariants depend on them changing synchronously with respect to other actions.
     * --- 但不需要锁定，因为没有内部不变式依赖于它们相对于其他动作进行同步更改。
     *
     * 意思就是保证可见性就ok了，没必要加锁
     */

    /**
     * Factory for new threads. All threads are created using this factory (via method addWorker).
     * --- 新线程的工厂。所有线程都是使用此工厂创建的（通过addWorker方法）。
     *
     * All callers must be prepared for addWorker to fail, which may reflect a system or user's policy limiting the number of threads.
     * --- 必须为所有调用程序做好准备，以使addWorker失败，这可能反映出系统或用户的策略限制了线程数。
     *
     * Even though it is not treated as an error, failure to create threads may result in new tasks being rejected or existing ones remaining stuck in the queue.
     * --- 即使未将其视为错误，创建线程的失败也可能导致新任务被拒绝或现有任务仍停留在队列中。
     *
     * We go further and preserve pool invariants even in the face of errors such as OutOfMemoryError, that might be thrown while trying to create threads.
     * --- 我们走得更远，即使遇到诸如OutOfMemoryError之类的错误（在尝试创建线程时可能抛出的错误），也要保留池不变式。
     *
     * Such errors are rather common due to the need to allocate a native stack in Thread.start, and users will want to perform clean pool shutdown to clean up.
     * --- 由于需要在Thread.start中分配本机堆栈，因此此类错误相当普遍，并且用户将需要执行清理池关闭来进行清理。
     *
     * There will likely be enough memory available for the cleanup code to complete without encountering yet another OutOfMemoryError.
     * --- 可能有足够的可用内存来完成清除代码，而不会遇到另一个OutOfMemoryError。
     *
     * 创建线程时会使用 线程工厂，当我们使用 Executors.newFix...  newCache... 创建线程池时，使用的是 DefaultThreadFactory
     * 一般不建议使用Default线程池，推荐自己实现ThreadFactory
     *
     * @see Executors.DefaultThreadFactory
     */
    private volatile ThreadFactory threadFactory;

    /**
     * Handler called when saturated or shutdown in execute.
     * 拒绝策略，juc包提供了4中方式，默认采用 AbortPolicy()..抛出异常的方式。
     *
     * @see AbortPolicy
     */
    private volatile RejectedExecutionHandler handler;

    /**
     * Timeout in nanoseconds for idle threads waiting for work.
     * --- 空闲线程等待工作的超时时间（以纳秒为单位）。
     *
     * Threads use this timeout when there are more than corePoolSize present or if allowCoreThreadTimeOut.
     * --- 当存在多于corePoolSize或allowCoreThreadTimeOut时，线程将使用此超时。
     *
     * Otherwise they wait forever for new work.
     * --- 否则，他们将永远等待新的工作。
     *
     * 空闲线程存活时间，当
     * allowCoreThreadTimeOut == false 时，会维护核心线程数量内的线程存活，超出部分会被超时。
     * allowCoreThreadTimeOut == true 核心数量内的线程 空闲时 也会被回收。
     *
     * @see ThreadPoolExecutor#allowCoreThreadTimeOut
     */
    private volatile long keepAliveTime;

    /**
     * If false (default), core threads stay alive even when idle.
     * --- 如果为false（默认），则即使处于空闲状态，核心线程也保持活动状态。
     *
     * If true, core threads use keepAliveTime to time out waiting for work.
     * --- 如果为true，则核心线程使用keepAliveTime来超时等待工作。
     *
     * 控制核心线程数量内的线程 是否可以被回收。true 可以，false不可以
     */
    private volatile boolean allowCoreThreadTimeOut;

    /**
     * Core pool size is the minimum number of workers to keep alive (and not allow to time out etc) unless allowCoreThreadTimeOut is set, in which case the minimum is zero.
     * --- 除非设置allowCoreThreadTimeOut，否则核心池大小是保持活动（不允许超时等）工作的最小数量，在这种情况下，最小值为零。
     *
     * 核心线程数量限制
     */
    private volatile int corePoolSize;

    /**
     * 线程池最大线程数量限制
     *
     * Maximum pool size. --- 最大池大小
     *
     * Note that the actual maximum is internally bounded by CAPACITY.
     * --- 请注意，实际最大值在内部受“容量”限制。
     *
     * @see ThreadPoolExecutor#CAPACITY
     */
    private volatile int maximumPoolSize;

    /**
     * The default rejected execution handler
     *
     * 缺省拒绝策略，采用的是AbortPolicy 抛出异常的方式。
     */
    private static final RejectedExecutionHandler defaultHandler = new AbortPolicy();

    /**
     * Permission required for callers of shutdown and shutdownNow.
     * We additionally require (see checkShutdownAccess) that callers
     * have permission to actually interrupt threads in the worker set
     * (as governed by Thread.interrupt, which relies on
     * ThreadGroup.checkAccess, which in turn relies on
     * SecurityManager.checkAccess). Shutdowns are attempted only if
     * these checks pass.
     *
     * All actual invocations of Thread.interrupt (see
     * interruptIdleWorkers and interruptWorkers) ignore
     * SecurityExceptions, meaning that the attempted interrupts
     * silently fail. In the case of shutdown, they should not fail
     * unless the SecurityManager has inconsistent policies, sometimes
     * allowing access to a thread and sometimes not. In such cases,
     * failure to actually interrupt threads may disable or delay full
     * termination. Other uses of interruptIdleWorkers are advisory,
     * and failure to actually interrupt will merely delay response to
     * configuration changes so is not handled exceptionally.
     *
     * 跟权限相关
     */
    private static final RuntimePermission shutdownPerm = new RuntimePermission("modifyThread");

    /**
     * 跟权限相关
     */
    /* The context to be used when executing the finalizer, or null. */
    private final AccessControlContext acc;

    /**
     * Class Worker mainly maintains interrupt control state for threads running tasks, along with other minor bookkeeping.
     * --- Class Worker主要维护线程运行任务的中断控制状态，以及其他次要簿记。
     *
     * This class opportunistically extends AbstractQueuedSynchronizer to simplify acquiring and releasing a lock surrounding each task execution.
     * --- 此类适时地扩展了AbstractQueuedSynchronizer以简化获取和释放围绕每个任务执行的锁。
     *
     * This protects against interrupts that are intended to wake up a worker thread waiting for a task from instead interrupting a task being run.
     * --- TODO 这可以防止旨在唤醒工作线程等待任务的中断，而不是中断正在运行的任务。?????
     *
     * We implement a simple non-reentrant mutual exclusion lock rather than use ReentrantLock
     * because we do not want worker tasks to be able to reacquire the lock when they invoke pool control methods like setCorePoolSize.
     * --- 我们实现了一个简单的非可重入互斥锁，而不是使用ReentrantLock，因为我们不希望辅助任务在调用诸如setCorePoolSize之类的池控制方法时能够重新获取该锁。
     *
     * Additionally, to suppress interrupts until the thread actually starts running tasks,
     * we initialize lock state to a negative value, and clear it upon start (in runWorker).
     * --- TODO 另外，为了抑制直到线程真正开始运行任务之前的中断，我们将锁定状态初始化为负值，并在启动时将其清除（在runWorker中）。？？？？
     */
    private final class Worker extends AbstractQueuedSynchronizer implements Runnable {
        //Worker采用了AQS的独占模式
        //独占模式：两个重要属性  state  和  ExclusiveOwnerThread（专有所有者线程）
        //state：
        // 0 时表示未被占用
        // > 0时表示被占用：加锁了
        // < 0 时 表示初始状态，这种情况下不能被抢锁。
        //ExclusiveOwnerThread:表示独占锁的线程。

        /**
         * This class will never be serialized, but we provide a
         * serialVersionUID to suppress a javac warning.
         */
        private static final long serialVersionUID = 6138294804551838833L;

        /**
         * worker内部封装的工作线程
         * Thread this worker is running in.  Null if factory fails. - 此工作程序正在其中运行的线程。如果工厂失败，则为null。
         */
        final Thread thread;

        /**
         * 假设firstTask不为空，那么当worker启动后（内部的线程启动)会优先执行firstTask，
         * 当执行完firstTask后，会到queue中去获取下一个任务。
         *
         * Initial task to run.  Possibly null. -- 要运行的初始任务。可能为null。
         *
         * @see ThreadPoolExecutor#runWorker(util.concurrent.ThreadPoolExecutor.Worker)
         */
        Runnable firstTask;

        /**
         * 记录当前worker所完成任务数量。
         * Per-thread task counter
         *
         * worker 终止的时候会累加到线程池的计数中
         */
        volatile long completedTasks;

        /**
         * Creates with given first task and thread from ThreadFactory.
         *
         * firstTask可以为null。为null 启动后会到queue中获取。
         *
         * @param firstTask the first task (null if none) 可以为空
         */
        Worker(Runnable firstTask) {
            //TODO 设置AQS独占模式为初始化中状态，这个时候 不能被抢占锁。
            setState(-1); // inhibit interrupts until runWorker --- 禁止中断，直到runWorker
            this.firstTask = firstTask;
            //使用线程工厂创建了一个线程，并且将当前worker 指定为 Runnable，
            //也就是说当thread启动的时候，会以worker.run()为入口。
            /**
             * @see Worker#run()
             */
            this.thread = getThreadFactory().newThread(this);
        }

        /**
         * 当worker启动时，会执行run()
         * Delegates main run loop to outer runWorker --- 将主运行循环委托给外部runWorker
         *
         * 线程启动之后会调用该方法
         *
         * @see Worker#Worker(java.lang.Runnable) 因为创建的时候传的就是当前对象
         * @see Thread#run()
         */
        public void run() {
            //ThreadPoolExecutor->runWorker() 这个是核心方法，等后面分析worker启动后逻辑时会以这里切入。
            //该方法其实就是在五险循环，直到没有任务
            runWorker(this);
        }

        // Lock methods --- 锁方法
        //
        // The value 0 represents the unlocked state.
        // The value 1 represents the locked state.
        // 判断当前worker的独占锁是否被独占。
        // 0 表示未被占用
        // 1 表示已占用

        /**
         * 独占锁是否被占用
         *
         * The value 0 represents the unlocked state.
         * The value 1 represents the locked state.
         * 判断当前worker的独占锁是否被独占。
         * 0 表示未被占用
         * 1 表示已占用
         *
         * @return
         */
        protected boolean isHeldExclusively() {
            return getState() != 0;
        }

        /**
         * 尝试去占用worker的独占锁
         * 返回值 表示是否抢占成功
         *
         * @param unused 没有使用
         * @return 成功失败
         * @see AbstractQueuedSynchronizer#tryAcquire(int)
         */
        protected boolean tryAcquire(int unused) {
            //使用CAS修改 AQS中的 state ，期望值为0(0时表示未被占用），
            //修改成功表示当前线程抢占成功
            if (compareAndSetState(0, 1)) {
                //抢占独占锁成功之后
                //设置 ExclusiveOwnerThread 为当前线程。
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        /**
         * 外部不会直接调用这个方法 这个方法是AQS 内调用的，
         * 外部调用unlock时 ，unlock->AQS.release() ->tryRelease()
         *
         * @param unused
         * @return
         */
        protected boolean tryRelease(int unused) {
            //表示独占线程不存在
            setExclusiveOwnerThread(null);
            //设置锁状态位0：表示没有哦被占用
            setState(0);
            return true;
        }

        /**
         * 加锁，加锁失败时，会阻塞当前线程，直到获取到锁为止。
         */
        public void lock() {
            acquire(1);
        }

        /**
         * 尝试去加锁，如果当前锁是未被持有状态，那么加锁成功后 会返回true，否则不会阻塞当前线程，直接返回false.
         *
         * @return
         */
        public boolean tryLock() {
            return tryAcquire(1);
        }

        /**
         * 一般情况下，咱们调用unlock 要保证 当前线程是持有锁的。
         * 特殊情况，当worker的state == -1 时，调用unlock 表示初始化state 设置state == 0
         * 启动worker之前会先调用unlock()这个方法。会强制刷新ExclusiveOwnerThread = null State=0
         */
        public void unlock() {
            release(1);
        }

        /**
         * 就是返回当前worker的lock是否被占用。
         *
         * @return
         */
        public boolean isLocked() {
            return isHeldExclusively();
        }

        void interruptIfStarted() {
            Thread t;
            if (
                //0 或者 1
                    getState() >= 0
                            &&
                            //线程不为null
                            (t = thread) != null
                            &&
                            //并且当前线程t的中断状态位false
                            !t.isInterrupted()) {
                try {
                    //未中断则发一个中断信号
                    t.interrupt();
                } catch (SecurityException ignore) {
                }
            }
        }
    }

    /************** Worker 类结束了  ******************/

    /*
     * Methods for setting control state
     * --- 设置控制状态的方法
     */

    /**
     * Transitions runState to given target, or leaves it alone if already at least the given target.
     *
     * -- 将runState转换为给定目标，但是如果当前的状态已经 >= targetState ，则什么都不做
     *
     * @param targetState the desired state, either SHUTDOWN or STOP -- 目标状态
     * (but not TIDYING or TERMINATED -- use tryTerminate for that)
     */
    private void advanceRunState(int targetState) {
        //自旋
        for (; ; ) {
            int c = ctl.get();

            if (
                //条件成立：假设targetState == SHUTDOWN，说明 当前线程池状态是 >= SHUTDOWN
                //条件不成立：假设targetState == SHUTDOWN ，说明当前线程池状态是RUNNING。
                //如果当前状态已经 >= targetState 则维持当前的状态不变
                    runStateAtLeast(c, targetState)
                            ||
                            //否则，使用cas改状态
                            //前提：假设targetState == SHUTDOWN，说明当前线程池状态是RUNNING。
                            ctl.compareAndSet(c, ctlOf(targetState, workerCountOf(c)))) {
                break;
            }
        }
    }

    /**
     * Transitions to TERMINATED state if either (SHUTDOWN and pool and queue empty) or (STOP and pool empty).
     * --- TODO 如果（状态位 SHUTDOWN 并且队列为空）或（状态为STOP 并且线程池为空），则转换为TERMINATED状态。？？？？？
     *
     * If otherwise eligible to terminate but workerCount is nonzero, interrupts an idle worker to ensure that shutdown signals propagate.
     * --- 如果可以终止，但workerCount非零，则中断空闲的worker，以确保关闭信号传播。
     *
     * This method must be called following any action that might make termination possible -- reducing worker count or removing tasks from the queue during shutdown.
     * --- 必须在可能终止操作的任何操作之后调用此方法-减少worker计数或在关闭期间从队列中删除任务。
     *
     * The method is non-private to allow access from ScheduledThreadPoolExecutor.
     */
    final void tryTerminate() {
        //自旋
        for (; ; ) {
            //获取最新ctl值
            int c = ctl.get();
            if (
                //条件一：isRunning(c)  成立，直接返回就行，线程池很正常！
                    isRunning(c)
                            ||
                            //条件二：runStateAtLeast(c, TIDYING) 当前状态 >= TIDYING
                            //说明 已经有其它线程 在执行 TIDYING -> TERMINATED状态了,当前线程直接回去。
                            runStateAtLeast(c, TIDYING)
                            ||
                            //条件三：(runStateOf(c) == SHUTDOWN && ! workQueue.isEmpty())
                            //SHUTDOWN特殊情况，如果是这种情况，直接回去。得等队列中的任务处理完毕后，再转化状态。
                            (runStateOf(c) == SHUTDOWN && !workQueue.isEmpty())) {
                return;
            }

            //什么情况会执行到这里？
            //1.线程池状态 >= STOP
            //2.线程池状态为 SHUTDOWN 且 队列已经空了

            //条件成立：当前线程池中的线程数量 > 0
            if (workerCountOf(c) != 0) { // Eligible to terminate - 有资格终止
                //当前线程池中的线程数量 > 0

                //中断一个空闲线程。
                //空闲线程，在哪空闲呢？ queue.take() | queue.poll()
                //1.唤醒后的线程 会在getTask()方法返回null
                //2.执行退出逻辑的时候会再次调用tryTerminate() 唤醒下一个空闲线程
                //3.因为线程池状态是 （线程池状态 >= STOP || 线程池状态为 SHUTDOWN 且 队列已经空了） 最终调用addWorker时，会失败。
                //最终空闲线程都会在这里退出，非空闲线程 当执行完当前task时，也会调用tryTerminate方法，有可能会走到这里。
                interruptIdleWorkers(ONLY_ONE);
                return;
            }

            //执行到这里的线程是谁？---- 最后一个线程！！！！！
            //workerCountOf(c) == 0 时，会来到这里。
            //最后一个退出的线程。 咱们知道，在 （线程池状态 >= STOP || 线程池状态为 SHUTDOWN 且 队列已经空了）
            //线程唤醒后，都会执行退出逻辑，退出过程中 会 先将 workerCount计数 -1 => ctl -1。
            //调用tryTerminate 方法之前，已经减过了，所以0时，表示这是最后一个退出的线程了。

            final ReentrantLock mainLock = this.mainLock;
            //获取线程池全局锁
            mainLock.lock();
            try {
                //设置线程池状态为TIDYING状态。
                if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {
                    try {
                        //调用钩子方法
                        terminated();
                    } finally {
                        //设置线程池状态为TERMINATED状态。
                        ctl.set(ctlOf(TERMINATED, 0));
                        /**
                         * 唤醒调用 awaitTermination() 方法的线程。
                         * @see ThreadPoolExecutor#awaitTermination(long, java.util.concurrent.TimeUnit)
                         */
                        termination.signalAll();
                    }
                    return;
                }
            } finally {
                //释放线程池全局锁。
                mainLock.unlock();
            }
            // else retry on failed CAS
        }//自旋结束
    }

    /*
     * Methods for controlling interrupts to worker threads.
     */

    /**
     * If there is a security manager, makes sure caller has
     * permission to shut down threads in general (see shutdownPerm).
     * If this passes, additionally makes sure the caller is allowed
     * to interrupt each worker thread. This might not be true even if
     * first check passed, if the SecurityManager treats some threads
     * specially.
     */
    private void checkShutdownAccess() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(shutdownPerm);
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                for (Worker w : workers) {
                    security.checkAccess(w.thread);
                }
            } finally {
                mainLock.unlock();
            }
        }
    }

    /**
     * Interrupts all threads, even if active. Ignores SecurityExceptions
     * (in which case some threads may remain uninterrupted).
     */
    private void interruptWorkers() {
        //可重入锁
        final ReentrantLock mainLock = this.mainLock;
        //获取线程池全局锁
        mainLock.lock();
        try {
            //遍历所有worker
            for (Worker w : workers) {
                //interruptIfStarted() 如果worker内的thread 是启动状态，则给它一个中断信号。。
                w.interruptIfStarted();
            }
        } finally {
            //释放线程池全局锁
            mainLock.unlock();
        }
    }

    /**
     * Interrupts threads that might be waiting for tasks (as
     * indicated by not being locked) so they can check for
     * termination or configuration changes. Ignores
     * SecurityExceptions (in which case some threads may remain
     * uninterrupted).
     *
     * @param onlyOne If true, interrupt at most one worker. This is
     * called only from tryTerminate when termination is otherwise
     * enabled but there are still other workers.  In this case, at
     * most one waiting worker is interrupted to propagate shutdown
     * signals in case all threads are currently waiting.
     * Interrupting any arbitrary thread ensures that newly arriving
     * workers since shutdown began will also eventually exit.
     * To guarantee eventual termination, it suffices to always
     * interrupt only one idle worker, but shutdown() interrupts all
     * idle workers so that redundant workers exit promptly, not
     * waiting for a straggler task to finish.
     */
    //onlyOne == true 说明只中断一个线程 ，false 则中断所有线程
    //共同前提，worker是空闲状态。
    private void interruptIdleWorkers(boolean onlyOne) {
        //可重入锁
        final ReentrantLock mainLock = this.mainLock;
        //持有全局锁
        mainLock.lock();
        try {
            //迭代所有worker
            for (Worker w : workers) {
                //获取当前worker的线程 保存到t
                Thread t = w.thread;
                if (
                    //条件一：条件成立：!t.isInterrupted()  == true  说明当前迭代的这个线程尚未中断。
                        !t.isInterrupted()
                                &&
                                //条件二：w.tryLock() 条件成立：说明当前worker处于空闲状态，可以去给它一个中断信号。
                                //目前worker内的线程 在 queue.take() | queue.poll() 阻塞中。
                                //因为worker执行task时，是加锁的!所以只有在空闲的时候才能成功拿到锁
                                w.tryLock()) {

                    try {
                        //给当前线程中断信号..处于queue阻塞的线程，会被唤醒，唤醒后，进入下一次自旋时，可能会return null。执行退出相关的逻辑。
                        t.interrupt();
                    } catch (SecurityException ignore) {
                        //ignore
                    } finally {
                        //释放worker的独占锁。
                        w.unlock();
                    }
                }

                //是否只中断一个
                if (onlyOne) {
                    break;
                }
            }
        } finally {
            //释放全局锁。
            mainLock.unlock();
        }
    }

    /**
     * Common form of interruptIdleWorkers, to avoid having to
     * remember what the boolean argument means.
     *
     * 避免忘记记住布尔参数含义的常用形式的interruptIdleWorkers。
     */
    private void interruptIdleWorkers() {
        interruptIdleWorkers(false);
    }

    private static final boolean ONLY_ONE = true;

    /*
     * Misc utilities, most of which are also exported to
     * ScheduledThreadPoolExecutor
     */

    /**
     * Invokes the rejected execution handler for the given command.
     * Package-protected for use by ScheduledThreadPoolExecutor.
     */
    final void reject(Runnable command) {
        //        handler.rejectedExecution(command, this);
    }

    /**
     * Performs any further cleanup following run state transition on
     * invocation of shutdown.  A no-op here, but used by
     * ScheduledThreadPoolExecutor to cancel delayed tasks.
     */
    void onShutdown() {
    }

    /**
     * State check needed by ScheduledThreadPoolExecutor to
     * enable running tasks during shutdown.
     *
     * @param shutdownOK true if should return true if SHUTDOWN
     */
    final boolean isRunningOrShutdown(boolean shutdownOK) {
        int rs = runStateOf(ctl.get());
        return rs == RUNNING || (rs == SHUTDOWN && shutdownOK);
    }

    /**
     * Drains the task queue into a new list, normally using
     * drainTo. But if the queue is a DelayQueue or any other kind of
     * queue for which poll or drainTo may fail to remove some
     * elements, it deletes them one by one.
     */
    private List<Runnable> drainQueue() {
        BlockingQueue<Runnable> q = workQueue;
        ArrayList<Runnable> taskList = new ArrayList<Runnable>();
        q.drainTo(taskList);
        if (!q.isEmpty()) {
            for (Runnable r : q.toArray(new Runnable[0])) {
                if (q.remove(r)) {
                    taskList.add(r);
                }
            }
        }
        return taskList;
    }

    /*
     * Methods for creating, running and cleaning up after workers
     */

    /**
     * Checks if a new worker can be added with respect to current
     * pool state and the given bound (either core or maximum). If so,
     * the worker count is adjusted accordingly, and, if possible, a
     * new worker is created and started, running firstTask as its
     * first task. This method returns false if the pool is stopped or
     * eligible to shut down. It also returns false if the thread
     * factory fails to create a thread when asked.  If the thread
     * creation fails, either due to the thread factory returning
     * null, or due to an exception (typically OutOfMemoryError in
     * Thread.start()), we roll back cleanly.
     *
     * @param firstTask the task the new thread should run first (or
     * null if none). Workers are created with an initial first task
     * (in method execute()) to bypass queuing when there are fewer
     * than corePoolSize threads (in which case we always start one),
     * or when the queue is full (in which case we must bypass queue).
     * Initially idle threads are usually created via
     * prestartCoreThread or to replace other dying workers.
     * @param firstTask 新线程应首先运行的任务（如果没有则为null）。
     * 当初始线程的数量少于corePoolSize（在这种情况下，我们始终启动一个线程），或队列已满时（在这种情况下，我们必须使用初始的第一个任务）（在execute（）方法中）创建工作线程，以绕过排队。绕过队列）。
     * 最初，空闲线程通常是通过prestartCoreThread创建的，或者用于替换其他垂死的工作线程。
     * @param core if true use corePoolSize as bound, else
     * maximumPoolSize. (A boolean indicator is used here rather than a
     * value to ensure reads of fresh values after checking other pool
     * state).
     *
     * 如果为true，请使用corePoolSize作为绑定，否则maximumPoolSize。 （此处使用布尔值指示符，而不是值，以确保在检查其他pool 状态后读取新值）。
     *
     * 如果为true，请使用corePoolSize作为限制，否则 maximumPoolSize
     * @return true if successful
     *
     * addWorker 即为创建线程的过程，会创建worker对象，并且将command作为firstTask
     * core == true 表示采用核心线程数量限制  false表示采用 maximumPoolSize
     */
    //firstTask 可以为null，表示启动worker之后，worker自动到queue中获取任务.. 如果不是null，则worker优先执行firstTask
    //core 采用的线程数限制 如果为true 采用 核心线程数限制  false采用 maximumPoolSize线程数限制.

    //返回值总结：
    //true 表示创建worker成功，且线程启动

    //false 表示创建失败。
    //1.线程池状态rs > SHUTDOWN (STOP/TIDYING/TERMINATION)
    //2.rs == SHUTDOWN 但是队列中已经没有任务了 或者 当前状态是SHUTDOWN且队列未空，但是firstTask不为null
    //3.当前线程池已经达到指定指标（corePoolSize 或者 maximumPoolSIze）
    //4.threadFactory 创建的线程是null
    private boolean addWorker(Runnable firstTask, boolean core) {
        retry:
        //自旋
        //判断当前线程池状态是否允许创建线程的事情。只有外层循环满足才能进入内层循环
        for (; ; ) {
            //获取当前ctl值保存到c
            int c = ctl.get();
            //获取当前线程池运行状态 保存到rs
            int rs = runStateOf(c);

            // Check if queue empty only if necessary.(仅在必要时检查队列是否为空。)

            if (
                //rs >= SHUTDOWN 成立：说明当前线程池状态不是running状态
                    rs >= SHUTDOWN//可能是：SHUTDOWN,STOP,DIYING,TERMINATED
                            &&
                            //前提：当前状态不是RUNNING
                            //如果该条件返回为ture，则表示：
                            //当前线程池是SHUTDOWN状态，但是队列里面还有任务尚未处理完，这个时候是允许添加worker，但是不允许再次提交task

                            //条件二：前置条件，当前的线程池状态不是running状态  ! (rs == SHUTDOWN && firstTask == null && ! workQueue.isEmpty())
                            //rs == SHUTDOWN && firstTask == null && ! workQueue.isEmpty()
                            //排除掉这种情况，当前线程池是SHUTDOWN状态，但是队列里面还有任务尚未处理完，这个时候是允许添加worker，但是不允许再次提交task。
                            !(rs == SHUTDOWN //当前线程池状态是SHUTDOWN状态
                                    && firstTask == null //提交的任务是空，addWorker这个方法可能不是execute调用的
                                    && !workQueue.isEmpty())//当前任务队列不是空
            ) {

                //什么情况下回返回false?
                //rs > SHUTDOWN
                //或者 rs == SHUTDOWN 但是队列中已经没有任务了
                //或者 rs == SHUTDOWN 且 firstTask != null
                return false;
            }
            //上面这些代码，就是判断 当前线程池状态 是否允许添加线程。

            //内部自旋
            //获取创建线程令牌的过程。
            for (; ; ) {
                //获取当前线程池中线程数量 保存到wc中
                int wc = workerCountOf(c);

                if (wc >= CAPACITY //wc >= CAPACITY 永远不成立，因为CAPACITY是一个5亿多大的数字
                        ||
                        //core == true ,判断当前线程数量是否>=corePoolSize，会拿核心线程数量做限制。
                        //core == false,判断当前线程数量是否>=maximumPoolSize，会拿最大线程数量做限制。
                        wc >= (core ? corePoolSize : maximumPoolSize)) {

                    //执行到这里，说明当前无法添加线程了，已经达到指定限制了
                    return false;
                }

                //条件成立：说明记录线程数量已经加1成功，相当于申请到了一块令牌。
                //条件失败：说明可能有其它线程，修改过ctl这个值了。
                //失败的时候可能发生过什么事？
                //1.其它线程execute() 申请过令牌了，在这之前。导致CAS失败
                //2.外部线程可能调用过 shutdown() 或者 shutdownNow() 导致线程池状态发生变化了，咱们知道 ctl 高3位表示状态，状态改变后，cas也会失败（因为期望值变化了）。
                if (compareAndIncrementWorkerCount(c)) {
                    //进入到这里面，一定是cas成功啦！申请到令牌了
                    //直接跳出了 retry 外部这个for自旋。

                    //条件成立：说明记录线程数量已经加1成功，相当于申请到了一块令牌。
                    break retry;
                }

                //CAS失败，没有成功的申请到令牌（ctl低29位计数+1失败）
                //获取最新的ctl值
                c = ctl.get();  // Re-read ctl(重读ctl)
                //判断当前线程池状态是否发生过变化,如果外部在这之前调用过shutdown. shutdownNow 会导致状态变化。
                if (runStateOf(c) != rs) {
                    //状态发生变化后，直接返回到外层循环，外层循环负责判断当前线程池状态，是否允许创建线程。
                    continue retry;
                }
                // else CAS failed due to workerCount change; retry inner loop
                // 否则CAS由于workerCount更改而失败；重试内部循环
            }
        }

        //表示创建的worker是否已经启动，false未启动  true启动
        boolean workerStarted = false;
        //表示创建的worker是否添加到池子（HashSet<Worker>）中了 默认false 未添加 true是添加。
        boolean workerAdded = false;

        //w表示后面创建worker的一个引用。
        Worker w = null;
        try {
            //创建Worker
            //执行完后，线程应该是已经创建好了。(通过线程工厂)
            w = new Worker(firstTask);

            //将新创建的worker节点的线程 赋值给 t
            final Thread t = w.thread;

            //为什么要做 t != null 这个判断？
            //为了防止ThreadFactory 实现类有bug，因为ThreadFactory 是一个接口，谁都可以实现。
            //万一哪个 小哥哥 脑子一热，有bug，创建出来的线程 是null、、
            //Doug lea考虑的比较全面。肯定会防止他自己的程序报空指针，所以这里一定要做！
            if (t != null) {
                //将全局锁的引用保存到mainLock
                final ReentrantLock mainLock = this.mainLock;
                //获取全局锁，如果其他线程占用了锁，则会阻塞
                //直到获取成功为止，同一时刻 操纵 线程池内部相关的操作，都必须持锁。
                mainLock.lock();
                //从这里加锁之后，其它线程 是无法修改当前线程池状态的。
                try {
                    // Recheck while holding lock.（持有锁时重新检查）
                    // Back out on ThreadFactory failure or if
                    // shut down before lock acquired.（如果factory创建线程失败或者线程池被shutdown了，请释放锁）
                    //获取最新线程池运行状态保存到rs中
                    int rs = runStateOf(ctl.get());

                    if (
                        //条件一：rs < SHUTDOWN 成立：最正常状态，当前线程池为RUNNING状态.
                            rs < SHUTDOWN //只有RUNNING才能满足
                                    ||
                                    //条件二：前置条件：当前线程池状态不是RUNNING状态。
                                    //(rs == SHUTDOWN && firstTask == null)  当前状态为SHUTDOWN状态且firstTask为空。
                                    //其实判断的就是SHUTDOWN状态下的特殊情况，
                                    //只不过这里不再判断队列是否为空了
                                    (rs == SHUTDOWN && firstTask == null)) {//言下之意就是当线程池是shutdown状态的时候，并且firstTask是null的时候也可以创建线程

                        //t.isAlive() 当线程start后，线程isAlive会返回true。
                        //防止脑子发热的程序员，ThreadFactory创建线程返回给外部之前，将线程start了。。
                        if (t.isAlive()) {
                            // precheck that t is startable（预检查t是否可启动）
                            throw new IllegalThreadStateException();
                        }

                        //将咱们创建的worker添加到线程池中。
                        workers.add(w);
                        //获取最新当前线程池线程数量
                        int s = workers.size();
                        //条件成立：说明当前线程数量是一个新高。更新largestPoolSize
                        if (s > largestPoolSize) {
                            //更新一个统计值
                            largestPoolSize = s;
                        }
                        //表示线程已经追加进线程池中了。
                        //workers.add(w) 就是added
                        workerAdded = true;
                    }
                } finally {
                    //释放线程池全局锁。
                    mainLock.unlock();
                }
                //条件成立:说明 添加worker成功
                //条件失败：说明线程池在lock之前，线程池状态发生了变化，导致添加失败。
                if (workerAdded) {
                    //成功后，则将创建的worker启动，线程启动。
                    t.start();
                    //启动标记设置为true
                    workerStarted = true;
                }
            }
        } finally {
            //条件成立：! workerStarted 说明启动失败，需要做清理工作。
            if (!workerStarted) {
                //失败时做什么清理工作？
                //1.释放令牌
                //2.将当前worker清理出workers集合
                addWorkerFailed(w);
            }
        }
        //返回新创建的线程是否启动。

        //返回值总结：
        //true 表示创建worker成功，且线程启动

        //false 表示创建失败。
        //1.线程池状态rs > SHUTDOWN (STOP/TIDYING/TERMINATION)
        //2.rs == SHUTDOWN 但是队列中已经没有任务了 或者 当前状态是SHUTDOWN且队列未空，但是firstTask不为null
        //3.当前线程池已经达到指定指标（corePoolSize 或者 maximumPoolSIze）
        //4.threadFactory 创建的线程是null
        return workerStarted;
    }

    /**
     * Rolls back the worker thread creation.
     * - removes worker from workers, if present
     * - decrements worker count
     * - rechecks for termination, in case the existence of this
     * worker was holding up termination
     *
     * 当 addWork 方法失败的时候会调用
     *
     * @see ThreadPoolExecutor#addWorker(java.lang.Runnable, boolean)
     */
    private void addWorkerFailed(Worker w) {
        final ReentrantLock mainLock = this.mainLock;
        //持有线程池全局锁，因为操作的是线程池相关的东西。
        mainLock.lock();
        try {
            //条件成立：需要将worker在workers中清理出去。
            if (w != null) {
                workers.remove(w);
            }
            //将线程池计数恢复-1，前面+1过，这里因为失败，所以要-1，相当于归还令牌。
            decrementWorkerCount();
            //回头讲，shutdown shutdownNow再说。
            tryTerminate();
        } finally {
            //释放线程池全局锁。
            mainLock.unlock();
        }
    }

    /**
     * Performs cleanup and bookkeeping for a dying worker. Called
     * only from worker threads. Unless completedAbruptly is set,
     * assumes that workerCount has already been adjusted to account
     * for exit.  This method removes thread from worker set, and
     * possibly terminates the pool or replaces the worker if either
     * it exited due to user task exception or if fewer than
     * corePoolSize workers are running or queue is non-empty but
     * there are no workers.
     *
     * @param w the worker 要退出的线程
     * @param completedAbruptly if the worker died due to user exception 是否突然退出,true表示执行任务的时候发生了异常
     * @see ThreadPoolExecutor#runWorker(util.concurrent.ThreadPoolExecutor.Worker)
     */
    private void processWorkerExit(Worker w, boolean completedAbruptly) {
        //条件成立：代表当前w 这个worker是发生异常退出的，task任务执行过程中向上抛出异常了..
        //异常退出时，ctl计数，并没有-1
        //正常退出的时候都已经执行了 -1
        if (completedAbruptly) {
            // If abrupt, then workerCount wasn't adjusted
            decrementWorkerCount();
        }

        //获取线程池的全局锁引用
        final ReentrantLock mainLock = this.mainLock;
        //加锁
        mainLock.lock();
        try {
            //将当前worker完成的task数量，汇总到线程池的completedTaskCount
            completedTaskCount += w.completedTasks;
            //将worker从池子中移除..
            workers.remove(w);
        } finally {
            //释放全局锁
            mainLock.unlock();
        }

        //TODO
        tryTerminate();

        //获取最新ctl值
        int c = ctl.get();
        //条件成立：当前线程池状态为 RUNNING 或者 SHUTDOWN状态
        if (runStateLessThan(c, STOP)) {
            //当前线程池状态为：RUNNING 或者 SHUTDOWN状态

            //条件成立：当前线程是正常退出..
            if (!completedAbruptly) {

                //min表示线程池最低持有的线程数量
                //allowCoreThreadTimeOut == true => 说明核心线程数内的线程，也会超时被回收。 min == 0
                //allowCoreThreadTimeOut == false => min == corePoolSize
                int min = allowCoreThreadTimeOut ? 0 : corePoolSize;

                //线程池状态：RUNNING SHUTDOWN
                //条件一：假设min == 0 成立
                //条件二：! workQueue.isEmpty() 说明任务队列中还有任务，最起码要留一个线程。
                if (min == 0 && !workQueue.isEmpty()) {
                    //说明任务队列中还有任务，起码留一个线程
                    min = 1;
                }

                //条件成立：线程池中还拥有足够的线程。
                //考虑一个问题： workerCountOf(c) >= min  =>  (0 >= 0) ?是否有可能存在这个情况？
                //有可能！
                //什么情况下发生呢？ 答：当线程池中的核心线程数是可以被回收的情况下，会出现这种情况，这种情况下，当前线程池中的线程数 会变为0
                //下次再提交任务时，会再创建线程。
                if (workerCountOf(c) >= min) {
                    return; // replacement not needed 无需更换
                }
            }

            //1.当前线程在执行task时 发生异常，这里一定要创建一个新worker顶上去。
            //2.!workQueue.isEmpty() 说明任务队列中还有任务，最起码要留一个线程。 当前状态为 RUNNING || SHUTDOWN
            //3.当前线程数量 < corePoolSize值，此时会创建线程，维护线程池数量在corePoolSize个。
            addWorker(null, false);
        }
    }

    /**
     * Performs blocking or timed wait for a task, depending on
     * current configuration settings, or returns null if this worker
     * must exit because of any of:
     * 1. There are more than maximumPoolSize workers (due to
     * a call to setMaximumPoolSize).
     * 2. The pool is stopped.
     * 3. The pool is shutdown and the queue is empty.
     * 4. This worker timed out waiting for a task, and timed-out
     * workers are subject to termination (that is,
     * {@code allowCoreThreadTimeOut || workerCount > corePoolSize})
     * both before and after the timed wait, and if the queue is
     * non-empty, this worker is not the last thread in the pool.
     *
     * @return task, or null if the worker must exit, in which case
     * workerCount is decremented
     */
    //什么情况下会返回null？
    //1.rs >= STOP 成立说明：当前的状态最低也是STOP状态，一定要返回null了
    //2.前置条件 状态是 SHUTDOWN ，workQueue.isEmpty()
    //3.线程池中的线程数量 超过 最大限制时，会有一部分线程返回Null
    //4.线程池中的线程数超过corePoolSize时，会有一部分线程 超时后，返回null。
    private Runnable getTask() {
        //表示当前线程获取任务是否超时 默认false true表示已超时
        //上一次循环可能返回true,导致下一次循环的时候发生不同的逻辑
        boolean timedOut = false; // Did the last poll() time out?

        //自旋
        for (; ; ) {
            //获取最新ctl值保存到c中。
            int c = ctl.get();
            //获取线程池当前运行状态
            int rs = runStateOf(c);

            // Check if queue empty only if necessary.

            if (
                //条件一：rs >= SHUTDOWN 条件成立：说明当前线程池是非RUNNING状态，可能是 SHUTDOWN/STOP....
                    rs >= SHUTDOWN
                            &&
                            //条件二：(rs >= STOP || workQueue.isEmpty())
                            (
                                    //2.1:rs >= STOP 成立说明：当前的状态最低也是STOP状态，一定要返回null了（不返回具体的任务了）
                                    rs >= STOP
                                            ||
                                            //2.2：前置条件 状态是 SHUTDOWN
                                            //条件成立：说明当前线程池状态为SHUTDOWN状态 且 任务队列已空，此时一定返回null。
                                            workQueue.isEmpty())) {

                /**
                 * TODO 返回null，runWorker方法就会将返回Null的线程执行线程退出线程池的逻辑。???????
                 * @see ThreadPoolExecutor#processWorkerExit(util.concurrent.ThreadPoolExecutor.Worker, boolean)
                 *
                 * 在满足上述条件的时候，获取不到task就会退出一个线程
                 */

                //使用CAS+死循环的方式让 ctl值 -1
                decrementWorkerCount();
                return null;
            }

            //执行到这里，有几种情况？
            //1.线程池是RUNNING状态
            //2.线程池是SHUTDOWN状态 但是队列还未空，此时可以创建线程。!!!!!!!!!!!

            //获取线程池中的线程数量
            int wc = workerCountOf(c);

            // Are workers subject to culling(淘汰)?
            //timed == true 表示当前这个线程 获取 task 时 是支持超时机制的，使用queue.poll(xxx,xxx)阻塞一段时间; 当获取task超时的情况下，下一次自旋就可能返回null了。
            //timed == false 表示当前这个线程 获取 task 时 是不支持超时机制的，当前线程会使用 queue.take();一直阻塞
            boolean timed =
                    //情况1：allowCoreThreadTimeOut == true 表示核心线程数量内的线程 也可以被回收。所有线程 都是使用queue.poll(xxx,xxx) 超时机制这种方式获取task.
                    //情况2：allowCoreThreadTimeOut == false 表示当前线程池会维护核心数量内的线程。
                    allowCoreThreadTimeOut
                            ||

                            //条件成立：当前线程池中的线程数量是大于核心线程数的，此时让所有路过这里的线程，都用poll 支持超时的方式去获取任务，
                            //这样，就可能会有一部分线程获取不到任务，获取不到任务 返回Null，然后..runWorker会执行线程退出逻辑。
                            wc > corePoolSize;

            if (
            /**
             * 条件一：(wc > maximumPoolSize || (timed && timedOut))
             * 1.1：wc > maximumPoolSize  为什么会成立？setMaximumPoolSize()方法，可能外部线程将线程池最大线程数设置为比初始化时的要小
             * 1.2: (timed && timedOut) 条件成立：前置条件，当前线程使用 poll方式获取task。上一次循环时  使用poll方式获取任务时，超时了
             * 条件一 为true 表示 线程可以被回收，达到回收标准，当确实需要回收时再回收。
             * @see ThreadPoolExecutor#setMaximumPoolSize(int)
             */
                    (wc > maximumPoolSize || (timed && timedOut))
                            &&

                            //条件二：(wc > 1 || workQueue.isEmpty())
                            //2.1: wc > 1  条件成立，说明当前线程池中还有其他线程，当前线程可以直接回收，返回null
                            //2.2: workQueue.isEmpty() 前置条件 wc == 1， 条件成立：说明当前任务队列 已经空了，最后一个线程，也可以放心的退出。
                            (wc > 1 || workQueue.isEmpty())) {

                //使用CAS机制 将 ctl值 -1 ,减1成功的线程，返回null
                //CAS成功的，返回Null
                //CAS失败？ 为什么会CAS失败？
                //1.其它线程先你一步退出了
                //2.线程池状态发生变化了。
                if (compareAndDecrementWorkerCount(c)) {
                    return null;
                }

                //再次自旋时，timed有可能就是false了，因为当前线程cas失败，很有可能是因为其它线程成功退出导致的，再次咨询时
                //检查发现，当前线程 就可能属于 不需要回收范围内了。
                continue;
            }

            try {
                //获取任务的逻辑

                Runnable r =
                        //是否可以超时，如果可以，则可能进行线程回收的操作
                        timed ?
                                workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) :
                                workQueue.take();

                //条件成立：返回任务
                if (r != null) {
                    return r;
                }
                //说明当前线程超时了...
                timedOut = true;
            } catch (InterruptedException retry) {
                timedOut = false;
            }
        }
    }

    /**
     * Main worker run loop.  Repeatedly gets tasks from queue and
     * executes them, while coping with a number of issues:(主工作者运行循环。反复从队列中获取任务并执行它们，同时解决许多问题：)
     *
     * 1. We may start out with an initial task, in which case we
     * don't need to get the first one. Otherwise, as long as pool is
     * running, we get tasks from getTask. If it returns null then the
     * worker exits due to changed pool state or configuration
     * parameters.  Other exits result from exception throws in
     * external code, in which case completedAbruptly holds, which
     * usually leads processWorkerExit to replace this thread.
     *
     * 1.我们可以从最初的任务开始，在这种情况下，我们不需要获得第一个任务。
     * 否则，只要池正在运行，我们就会从getTask获得任务。
     * 如果返回null，则工作器由于池状态或配置参数更改而退出。
     * 其他退出是由外部代码中的异常引发导致的，在这种情况下，completedAbruptly成立，这通常导致processWorkerExit替换此线程。
     *
     * 2. Before running any task, the lock is acquired to prevent
     * other pool interrupts while the task is executing, and then we
     * ensure that unless pool is stopping, this thread does not have
     * its interrupt set.
     *
     * 2.在运行任何任务之前，先获取锁，以防止任务执行时其他池中断，
     * 然后确保除非池正在停止，否则此线程不会设置其中断。
     *
     * 3. Each task run is preceded by a call to beforeExecute, which
     * might throw an exception, in which case we cause thread to die
     * (breaking loop with completedAbruptly true) without processing
     * the task.
     *
     * 3.每个任务运行之前都会调用beforeExecute，这可能会引发异常，在这种情况下，
     * 我们将导致线程死掉（中断带有completelyAbruptly true的循环）而不处理该任务。
     *
     * 4. Assuming beforeExecute completes normally, we run the task,
     * gathering any of its thrown exceptions to send to afterExecute.
     * We separately handle RuntimeException, Error (both of which the
     * specs guarantee that we trap) and arbitrary Throwables.
     * Because we cannot rethrow Throwables within Runnable.run, we
     * wrap them within Errors on the way out (to the thread's
     * UncaughtExceptionHandler).  Any thrown exception also
     * conservatively causes thread to die.
     *
     * 4.假设beforeExecute正常完成，我们运行任务，收集其引发的任何异常以发送给afterExecute。
     * 我们分别处理RuntimeException，Error（规范保证我们可以捕获它们）和任意Throwables。
     * 因为我们不能在Throwables.run中抛出Throwables，所以我们将它们包装在Errors中（输出到线程的UncaughtExceptionHandler）。
     * 任何抛出的异常也会保守地导致线程死亡。
     *
     * 5. After task.run completes, we call afterExecute, which may
     * also throw an exception, which will also cause thread to
     * die. According to JLS Sec 14.20, this exception is the one that
     * will be in effect even if task.run throws.
     *
     * 5. task.run完成后，我们调用afterExecute，这也可能引发异常，这也将导致线程死亡。
     * 根据JLS Sec 14.20，此异常是即使task.run抛出也会生效的异常。
     *
     * The net effect of the exception mechanics is that afterExecute
     * and the thread's UncaughtExceptionHandler have as accurate
     * information as we can provide about any problems encountered by
     * user code.
     *
     * 异常机制的最终结果是afterExecute和线程的UncaughtExceptionHandler具有与我们所能提供的有关用户代码遇到的任何问题的准确信息。
     *
     * @param w the worker 就是启动worker
     */
    //w 就是启动worker
    final void runWorker(Worker w) {
        //wt == w.thread
        //TODO 跟 w 中的线程是同一个？？？
        Thread wt = Thread.currentThread();
        //将初始执行task赋值给task
        Runnable task = w.firstTask;
        //清空当前w.firstTask引用
        w.firstTask = null;

        //这里为什么先调用unlock?
        //就是为了初始化worker
        //state == 0 和 exclusiveOwnerThread ==null
        w.unlock(); // allow interrupts

        //是否是突然退出，
        //true->发生异常了，当前线程是突然退出，回头需要做一些处理
        //false->正常退出。
        boolean completedAbruptly = true;

        try {
            while (
                //条件一：task != null 指的就是firstTask是不是null，如果不是null，则进入循环体先执行firstTask任务。
                    task != null
                            ||
                            //条件二：(task = getTask()) != null
                            //条件成立：说明当前线程在queue中获取任务成功，getTask这个方法是一个会阻塞线程的方法
                            //getTask如果返回null，当前线程需要执行结束逻辑。
                            (task = getTask()) != null) { //循环不断的去获取任务，并且执行，执行完之后继续循环获取任务
                //worker设置独占锁 为当前线程
                //为什么要设置独占锁呢？
                //shutdown时会判断当前worker状态，根据独占锁是否空闲来判断当前worker是否正在工作。
                w.lock();

                // If pool is stopping, ensure thread is interrupted;
                // if not, ensure thread is not interrupted.  This
                // requires a recheck in second case to deal with
                // shutdownNow race while clearing interrupt
                // 如果池正在停止，请确保线程被中断；如果没有，请确保线程不被中断。这需要在第二种情况下重新检查以处理shutdownNow竞赛，同时清除中断

                if (
                        (
                                //条件一：runStateAtLeast(ctl.get(), STOP)  说明线程池目前处于STOP/TIDYING/TERMINATION 此时线程一定要给它一个中断信号
                                runStateAtLeast(ctl.get(), STOP)
                                        ||

                                        // (Thread.interrupted() && runStateAtLeast(ctl.get(), STOP)) 在干吗呢？
                                        // 其实它在强制刷新当前线程的中断标记位 false，因为有可能上一次执行task时，业务代码里面将当前线程的中断标记位 设置为了 true，且没有处理
                                        // 这里一定要强制刷新一下。就不会再影响到后面的task了。

                                        //假设：Thread.interrupted() == true  且 runStateAtLeast(ctl.get(), STOP)) == true
                                        //这种情况有发生几率么？
                                        //有可能，因为外部线程在 第一次 (runStateAtLeast(ctl.get(), STOP) == false 后，有机会调用shutdown 、shutdownNow方法，将线程池状态修改
                                        //这个时候，也会将当前线程的中断标记位 再次设置回 中断状态。
                                        (
                                                //Thread.interrupted()：获取中断状态，且设置中断状态为false,连续调用2池，则第二次肯定返回false
                                                //假设：runStateAtLeast(ctl.get(), STOP) == false，则会执行到这里
                                                Thread.interrupted()
                                                        &&
                                                        // runStateAtLeast(ctl.get(), STOP) 大概率这里还是false.
                                                        runStateAtLeast(ctl.get(), STOP)
                                        )
                        )

                                &&

                                //wt.isInterrupted():返回该线程是否已经设置中断状态
                                //条件一成立：runStateAtLeast(ctl.get(), STOP)&& !wt.isInterrupted()
                                //上面如果成立：说明当前线程池状态是>=STOP 且 当前线程是未设置中断状态的，此时需要进入到if里面，给当前线程一个中断。
                                !wt.isInterrupted()
                ) {

                    //此时线程一定要给它一个中断信号
                    wt.interrupt();
                }

                //开始执行一个任务
                try {
                    //钩子方法，留给子类实现的
                    beforeExecute(wt, task);
                    //表示异常情况，如果thrown不为空，表示 task运行过程中 向上层抛出异常了。
                    Throwable thrown = null;
                    try {
                        //task 可能是FutureTask 也可能是 普通的Runnable接口实现类。
                        //如果前面是通过submit()提交的 runnable/callable 会被封装成 FutureTask。这个不清楚，请看上一期，在b站。
                        task.run();
                    } catch (RuntimeException x) {
                        thrown = x;
                        throw x;
                    } catch (Error x) {
                        thrown = x;
                        throw x;
                    } catch (Throwable x) {
                        thrown = x;
                        throw new Error(x);
                    } finally {
                        //钩子方法，留给子类实现的
                        afterExecute(task, thrown);
                    }
                } finally {
                    //将局部变量task置为Null
                    task = null;
                    //更新worker完成任务数量
                    w.completedTasks++;

                    //worker处理完一个任务后，会释放掉独占锁
                    //1.正常情况下，会再次回到getTask()那里获取任务  while(getTask...)
                    //2.task.run()时内部抛出异常了..
                    w.unlock();
                }
                //执行一个任务结束，继续获取任务

            }// while 循环结束，可能继续循环，可能往下执行

            //如果循环正常结束，就表示没有发生异常
            //什么情况下，会来到这里？
            //getTask()方法返回null时，说明当前线程应该执行退出逻辑了。
            //表示当前线程正常退出
            completedAbruptly = false;
        } finally {

            //task.run()内部抛出异常时，直接从 w.unlock() 那里 跳到这一行。
            //正常退出 completedAbruptly == false
            //异常退出 completedAbruptly == true
            processWorkerExit(w, completedAbruptly);
        }
    }

    // Public constructors and methods

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters and default thread factory and rejected execution handler.
     * It may be more convenient to use one of the {@link Executors} factory
     * methods instead of this general purpose constructor.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     * if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     * pool
     * @param keepAliveTime when the number of threads is greater than
     * the core, this is the maximum time that excess idle threads
     * will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     * executed.  This queue will hold only the {@code Runnable}
     * tasks submitted by the {@code execute} method.
     * @throws IllegalArgumentException if one of the following holds:<br>
     * {@code corePoolSize < 0}<br>
     * {@code keepAliveTime < 0}<br>
     * {@code maximumPoolSize <= 0}<br>
     * {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            TimeUnit unit,
            BlockingQueue<Runnable> workQueue) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, Executors.defaultThreadFactory(), defaultHandler);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters and default rejected execution handler.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     * if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     * pool
     * @param keepAliveTime when the number of threads is greater than
     * the core, this is the maximum time that excess idle threads
     * will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     * executed.  This queue will hold only the {@code Runnable}
     * tasks submitted by the {@code execute} method.
     * @param threadFactory the factory to use when the executor
     * creates a new thread
     * @throws IllegalArgumentException if one of the following holds:<br>
     * {@code corePoolSize < 0}<br>
     * {@code keepAliveTime < 0}<br>
     * {@code maximumPoolSize <= 0}<br>
     * {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     * or {@code threadFactory} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            TimeUnit unit,
            BlockingQueue<Runnable> workQueue,
            ThreadFactory threadFactory) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                threadFactory, defaultHandler);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters and default thread factory.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     * if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     * pool
     * @param keepAliveTime when the number of threads is greater than
     * the core, this is the maximum time that excess idle threads
     * will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     * executed.  This queue will hold only the {@code Runnable}
     * tasks submitted by the {@code execute} method.
     * @param handler the handler to use when execution is blocked
     * because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if one of the following holds:<br>
     * {@code corePoolSize < 0}<br>
     * {@code keepAliveTime < 0}<br>
     * {@code maximumPoolSize <= 0}<br>
     * {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     * or {@code handler} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            TimeUnit unit,
            BlockingQueue<Runnable> workQueue,
            RejectedExecutionHandler handler) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                Executors.defaultThreadFactory(), handler);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     * if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     * pool
     * @param keepAliveTime when the number of threads is greater than
     * the core, this is the maximum time that excess idle threads
     * will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     * executed.  This queue will hold only the {@code Runnable}
     * tasks submitted by the {@code execute} method.
     * @param threadFactory the factory to use when the executor
     * creates a new thread
     * @param handler the handler to use when execution is blocked
     * because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if one of the following holds:<br>
     * {@code corePoolSize < 0}<br>
     * {@code keepAliveTime < 0}<br>
     * {@code maximumPoolSize <= 0}<br>
     * {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     * or {@code threadFactory} or {@code handler} is null
     */
    public ThreadPoolExecutor(int corePoolSize,//核心线程数限制
            int maximumPoolSize,//最大线程限制
            long keepAliveTime,//空闲线程存活时间
            TimeUnit unit,//时间单位 seconds nano..
            BlockingQueue<Runnable> workQueue,//任务队列
            ThreadFactory threadFactory,//线程工厂
            RejectedExecutionHandler handler/*拒绝策略*/) {
        //判断参数是否越界
        if (corePoolSize < 0 ||
                maximumPoolSize <= 0 ||
                maximumPoolSize < corePoolSize || //但是是可以相等的
                keepAliveTime < 0) {
            throw new IllegalArgumentException();
        }

        //工作队列 和 线程工厂 和 拒绝策略 都不能为空。
        if (workQueue == null || threadFactory == null || handler == null) {
            throw new NullPointerException();
        }

        this.acc = System.getSecurityManager() == null ?
                null :
                AccessController.getContext();

        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;
    }

    /**
     * Executes the given task sometime in the future.
     *
     * The task may execute in a new thread or in an existing pooled thread.
     * --- 该任务可以在新线程或现有池线程中执行。
     *
     * If the task cannot be submitted for execution, either because this executor has been shutdown or because its capacity has been reached,
     * --- 如果由于该执行器已关闭或已达到其能力而无法提交任务执行，
     *
     * the task is handled by the current {@code RejectedExecutionHandler}.
     * --- 任务由当前的{@code RejectedExecutionHandler}处理。
     *
     * @param command the task to execute 要执行的任务，可以是普通的Runnable 实现类，也可以是 FutureTask
     * @throws RejectedExecutionException at discretion of {@code RejectedExecutionHandler}, if the task cannot be accepted for execution
     * @throws NullPointerException if {@code command} is null
     */
    public void execute(Runnable command) {
        //非空判断..
        if (command == null) {
            throw new NullPointerException();
        }
        /**
         * 3.如果我们无法将任务排队，则尝试添加一个新线程。如果失败，我们知道我们已关闭或已饱和，因此拒绝该任务。
         *
         * Proceed in 3 steps:
         *
         * 1. If fewer than corePoolSize threads are running, try to start a new thread with the given command as its first task.
         * ---如果正在运行的线程少于corePoolSize个，尝试使用给定 任务 作为其 first task 来启动新线程。
         *
         *    The call to addWorker atomically checks runState and workerCount,
         *    and so prevents false alarms that would add threads when it shouldn't, by returning false.
         *--- 对addWorker的调用从原子上检查runState和workerCount，从而通过返回false来防止在不应该添加线程的情况下发出虚假警报。
         *
         * 2. If a task can be successfully queued, then we still need to double-check whether we should have added a thread (because existing ones died since last checking) or that the pool shut down since entry into this method.
         * ---如果一个任务可以成功入队，那么我们仍然需要仔细检查是否应该添加一个线程（因为现有线程自上次检查后就死掉了）或自从进入该方法以来该池已关闭。
         *
         * So we recheck state and if necessary roll back the enqueuing if stopped, or start a new thread if there are none.
         * --- 因此，我们重新检查状态，并在必要时回滚队列（如果已停止），或者在没有线程的情况下启动新线程。
         *
         * 3. If we cannot queue task, then we try to add a new thread.  If it fails, we know we are shut down or saturated and so reject the task.
         * --- 如果我们无法将任务排队，则尝试添加一个新线程。如果失败，我们知道我们已关闭或已饱和，因此拒绝该任务。
         */

        //获取ctl最新值赋值给c，ctl ：高3位 表示线程池状态，低29位表示当前线程池线程数量。
        int c = ctl.get();

        //workerCountOf(c)：获取出当前线程数量
        if (workerCountOf(c) < corePoolSize) {//条件成立：表示当前线程数量小于核心线程数，此次提交任务，直接创建一个新的worker，对应线程池中多了一个新的线程。
            //addWorker 即为创建线程的过程，会创建worker对象，并且将command作为firstTask
            //core == true 表示采用核心线程数量限制  false表示采用 maximumPoolSize
            if (addWorker(command, true)) {
                //创建成功后，直接返回。addWorker方法里面会启动新创建的worker，将firstTask执行。
                return;
            }

            //执行到这条语句，说明addWorker一定是失败了...
            //有几种可能呢？？

            //1.存在并发现象，execute方法是可能有多个线程同时调用的，当workerCountOf(c) < corePoolSize成立后，
            //其它线程可能也成立了，并且向线程池中创建了worker。这个时候线程池中的核心线程数已经达到最大限制，所以失败了...

            //2.当前线程池状态发生改变了。 RUNNING SHUTDOWN STOP TIDYING　TERMINATION
            //当线程池状态是 非RUNNING 状态时，addWorker(firstTask!=null, true|false) 一定会失败。
            //SHUTDOWN 状态下，也有可能创建成功。前提 firstTask == null 而且当前 queue  不为空。特殊情况。
            c = ctl.get();
        }

        //执行到这里有几种情况？
        //1.当前线程数量已经达到corePoolSize
        //2.addWorker失败..

        //条件成立：说明当前线程池处于running状态，则尝试将 task 放入到workQueue中。
        if (isRunning(c) && workQueue.offer(command)) {
            //执行到这里，说明offer提交任务成功了..
            //当前线程是 RUNNING 状态，并且任务入队成功

            //再次获取ctl保存到recheck。
            int recheck = ctl.get();

            if (
                //条件一：! isRunning(recheck) 成立：说明你提交到队列之后，线程池状态被外部线程给修改 比如：shutdown() shutdownNow()
                //这种情况 需要把刚刚提交的任务删除掉。
                    !isRunning(recheck)
                            &&
                            //条件二：remove(command) 有可能成功，也有可能失败
                            //成功：提交之后，线程池中的线程还未消费（处理）
                            //失败：提交之后，在shutdown() shutdownNow()之前，就被线程池中的线程 给处理了。
                            remove(command)) {
                //提交之后线程池状态为 非running 且 任务出队（删除）成功，走个拒绝策略。
                reject(command);
            }

            //有几种情况会到这里？
            //1.当前线程池是running状态(这个概率最大)
            //2.线程池状态是非running状态 但是remove提交的任务失败.

            //担心 当前线程池是running状态，但是线程池中的存活线程数量是0，这个时候，如果是0的话，会很尴尬，任务没线程去跑了,
            //这里其实是一个担保机制，保证线程池在running状态下，最起码得有一个线程在工作。
            else if (workerCountOf(recheck) == 0) {
                addWorker(null, false);
            }
        }

        //执行到这里，有几种情况？
        //1.offer失败
        //2.当前线程池是非running状态

        //1.offer失败，需要做什么？ 说明当前queue 满了！这个时候 如果当前线程数量尚未达到maximumPoolSize的话，会创建新的worker直接执行command
        //假设当前线程数量达到maximumPoolSize的话，这里也会失败，也走拒绝策略。

        //2.线程池状态为非running状态，这个时候因为 command != null addWorker 一定是返回false。
        else if (!addWorker(command, false)) {
            reject(command);
        }
    }

    /**
     * Initiates an orderly shutdown in which previously submitted
     * tasks are executed, but no new tasks will be accepted.
     * Invocation has no additional effect if already shut down.
     *
     * <p>This method does not wait for previously submitted tasks to
     * complete execution.  Use {@link #awaitTermination awaitTermination}
     * to do that.
     *
     * @throws SecurityException {@inheritDoc}
     */
    public void shutdown() {
        final ReentrantLock mainLock = this.mainLock;
        //获取线程池全局锁
        mainLock.lock();
        try {
            checkShutdownAccess();
            //设置线程池状态为SHUTDOWN
            advanceRunState(SHUTDOWN);

            //中断空闲线程
            interruptIdleWorkers();

            //空方法，子类可以扩展
            onShutdown(); // hook for ScheduledThreadPoolExecutor
        } finally {
            //释放线程池全局锁
            mainLock.unlock();
        }
        //回头说..
        tryTerminate();
    }

    /**
     * Attempts to stop all actively executing tasks, halts the
     * processing of waiting tasks, and returns a list of the tasks
     * that were awaiting execution. These tasks are drained (removed)
     * from the task queue upon return from this method.
     *
     * <p>This method does not wait for actively executing tasks to
     * terminate.  Use {@link #awaitTermination awaitTermination} to
     * do that.
     *
     * <p>There are no guarantees beyond best-effort attempts to stop
     * processing actively executing tasks.  This implementation
     * cancels tasks via {@link Thread#interrupt}, so any task that
     * fails to respond to interrupts may never terminate.
     *
     * @throws SecurityException {@inheritDoc}
     */
    public List<Runnable> shutdownNow() {
        //返回值引用
        List<Runnable> tasks;
        final ReentrantLock mainLock = this.mainLock;
        //获取线程池全局锁
        mainLock.lock();
        try {
            checkShutdownAccess();
            //设置线程池状态为STOP
            advanceRunState(STOP);

            //中断线程池中
            // 所有 ！！！！！
            //线程
            interruptWorkers();

            //导出未处理的task
            tasks = drainQueue();
        } finally {
            mainLock.unlock();
        }

        tryTerminate();
        //返回当前任务队列中 未处理的任务。
        return tasks;
    }

    public boolean isShutdown() {
        return !isRunning(ctl.get());
    }

    /**
     * Returns true if this executor is in the process of terminating
     * after {@link #shutdown} or {@link #shutdownNow} but has not
     * completely terminated.  This method may be useful for
     * debugging. A return of {@code true} reported a sufficient
     * period after shutdown may indicate that submitted tasks have
     * ignored or suppressed interruption, causing this executor not
     * to properly terminate.
     *
     * @return {@code true} if terminating but not yet terminated
     */
    public boolean isTerminating() {
        int c = ctl.get();
        return !isRunning(c) && runStateLessThan(c, TERMINATED);
    }

    public boolean isTerminated() {
        return runStateAtLeast(ctl.get(), TERMINATED);
    }

    /**
     * 当外部线程调用  awaitTermination() {@link ThreadPoolExecutor#awaitTermination(long, java.util.concurrent.TimeUnit)}方法时，
     * 外部线程会等待当前线程池状态为 Termination 为止。
     * 等待是如何实现的？ 就是将外部线程 封装成 waitNode 放入到 Condition 队列中了， waitNode.Thread 就是外部线程，会被park掉（处于WAITING状态）。
     * 当线程池 状态 变为 Termination时，会去唤醒这些线程。通过 termination.signalAll() ，
     * {@link ThreadPoolExecutor#tryTerminate()}唤醒之后这些线程会进入到 阻塞队列，然后头结点会去抢占mainLock。
     * 抢占到的线程，会继续执行awaitTermination() 后面程序。这些线程最后，都会正常执行。
     *
     *
     * 简单理解：
     * termination.await() 会将线程阻塞在这。
     * termination.signalAll() 会将阻塞在这的线程依次唤醒
     *
     * @see ThreadPoolExecutor#tryTerminate()
     * @see ThreadPoolExecutor#awaitTermination(long, java.util.concurrent.TimeUnit)
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (; ; ) {
                if (runStateAtLeast(ctl.get(), TERMINATED)) {
                    return true;
                }
                if (nanos <= 0) {
                    return false;
                }
                nanos = termination.awaitNanos(nanos);
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Invokes {@code shutdown} when this executor is no longer
     * referenced and it has no threads.
     */
    protected void finalize() {
        SecurityManager sm = System.getSecurityManager();
        if (sm == null || acc == null) {
            shutdown();
        } else {
            PrivilegedAction<Void> pa = () -> {
                shutdown();
                return null;
            };
            AccessController.doPrivileged(pa, acc);
        }
    }

    /**
     * Sets the thread factory used to create new threads.
     *
     * @param threadFactory the new thread factory
     * @throws NullPointerException if threadFactory is null
     * @see #getThreadFactory
     */
    public void setThreadFactory(ThreadFactory threadFactory) {
        if (threadFactory == null) {
            throw new NullPointerException();
        }
        this.threadFactory = threadFactory;
    }

    /**
     * Returns the thread factory used to create new threads.
     *
     * @return the current thread factory
     * @see #setThreadFactory(ThreadFactory)
     */
    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    /**
     * Sets a new handler for unexecutable tasks.
     *
     * @param handler the new handler
     * @throws NullPointerException if handler is null
     * @see #getRejectedExecutionHandler
     */
    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        if (handler == null) {
            throw new NullPointerException();
        }
        this.handler = handler;
    }

    /**
     * Returns the current handler for unexecutable tasks.
     *
     * @return the current handler
     * @see #setRejectedExecutionHandler(RejectedExecutionHandler)
     */
    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return handler;
    }

    /**
     * Sets the core number of threads.  This overrides any value set
     * in the constructor.  If the new value is smaller than the
     * current value, excess existing threads will be terminated when
     * they next become idle.  If larger, new threads will, if needed,
     * be started to execute any queued tasks.
     *
     * @param corePoolSize the new core size
     * @throws IllegalArgumentException if {@code corePoolSize < 0}
     * @see #getCorePoolSize
     */
    public void setCorePoolSize(int corePoolSize) {
        if (corePoolSize < 0) {
            throw new IllegalArgumentException();
        }
        int delta = corePoolSize - this.corePoolSize;
        this.corePoolSize = corePoolSize;
        if (workerCountOf(ctl.get()) > corePoolSize) {
            interruptIdleWorkers();
        } else if (delta > 0) {
            // We don't really know how many new threads are "needed".
            // As a heuristic, prestart enough new workers (up to new
            // core size) to handle the current number of tasks in
            // queue, but stop if queue becomes empty while doing so.
            int k = Math.min(delta, workQueue.size());
            while (k-- > 0 && addWorker(null, true)) {
                if (workQueue.isEmpty()) {
                    break;
                }
            }
        }
    }

    /**
     * Returns the core number of threads.
     *
     * @return the core number of threads
     * @see #setCorePoolSize
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * Starts a core thread, causing it to idly wait for work. This
     * overrides the default policy of starting core threads only when
     * new tasks are executed. This method will return {@code false}
     * if all core threads have already been started.
     *
     * @return {@code true} if a thread was started
     */
    public boolean prestartCoreThread() {
        return workerCountOf(ctl.get()) < corePoolSize && addWorker(null, true);
    }

    /**
     * Same as prestartCoreThread except arranges that at least one
     * thread is started even if corePoolSize is 0.
     */
    void ensurePrestart() {
        int wc = workerCountOf(ctl.get());
        if (wc < corePoolSize) {
            addWorker(null, true);
        } else if (wc == 0) {
            addWorker(null, false);
        }
    }

    /**
     * Starts all core threads, causing them to idly wait for work. This
     * overrides the default policy of starting core threads only when
     * new tasks are executed.
     *
     * @return the number of threads started
     */
    public int prestartAllCoreThreads() {
        int n = 0;
        while (addWorker(null, true)) {
            ++n;
        }
        return n;
    }

    /**
     * Returns true if this pool allows core threads to time out and
     * terminate if no tasks arrive within the keepAlive time, being
     * replaced if needed when new tasks arrive. When true, the same
     * keep-alive policy applying to non-core threads applies also to
     * core threads. When false (the default), core threads are never
     * terminated due to lack of incoming tasks.
     *
     * @return {@code true} if core threads are allowed to time out,
     * else {@code false}
     * @since 1.6
     */
    public boolean allowsCoreThreadTimeOut() {
        return allowCoreThreadTimeOut;
    }

    /**
     * Sets the policy governing whether core threads may time out and
     * terminate if no tasks arrive within the keep-alive time, being
     * replaced if needed when new tasks arrive. When false, core
     * threads are never terminated due to lack of incoming
     * tasks. When true, the same keep-alive policy applying to
     * non-core threads applies also to core threads. To avoid
     * continual thread replacement, the keep-alive time must be
     * greater than zero when setting {@code true}. This method
     * should in general be called before the pool is actively used.
     *
     * @param value {@code true} if should time out, else {@code false}
     * @throws IllegalArgumentException if value is {@code true}
     * and the current keep-alive time is not greater than zero
     * @since 1.6
     */
    public void allowCoreThreadTimeOut(boolean value) {
        if (value && keepAliveTime <= 0) {
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        }
        if (value != allowCoreThreadTimeOut) {
            allowCoreThreadTimeOut = value;
            if (value) {
                interruptIdleWorkers();
            }
        }
    }

    /**
     * Sets the maximum allowed number of threads. This overrides any
     * value set in the constructor. If the new value is smaller than
     * the current value, excess existing threads will be
     * terminated when they next become idle.
     *
     * @param maximumPoolSize the new maximum
     * @throws IllegalArgumentException if the new maximum is
     * less than or equal to zero, or
     * less than the {@linkplain #getCorePoolSize core pool size}
     * @see #getMaximumPoolSize
     */
    public void setMaximumPoolSize(int maximumPoolSize) {
        if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize) {
            throw new IllegalArgumentException();
        }
        this.maximumPoolSize = maximumPoolSize;
        if (workerCountOf(ctl.get()) > maximumPoolSize) {
            interruptIdleWorkers();
        }
    }

    /**
     * Returns the maximum allowed number of threads.
     *
     * @return the maximum allowed number of threads
     * @see #setMaximumPoolSize
     */
    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    /**
     * Sets the time limit for which threads may remain idle before
     * being terminated.  If there are more than the core number of
     * threads currently in the pool, after waiting this amount of
     * time without processing a task, excess threads will be
     * terminated.  This overrides any value set in the constructor.
     *
     * @param time the time to wait.  A time value of zero will cause
     * excess threads to terminate immediately after executing tasks.
     * @param unit the time unit of the {@code time} argument
     * @throws IllegalArgumentException if {@code time} less than zero or
     * if {@code time} is zero and {@code allowsCoreThreadTimeOut}
     * @see #getKeepAliveTime(TimeUnit)
     */
    public void setKeepAliveTime(long time, TimeUnit unit) {
        if (time < 0) {
            throw new IllegalArgumentException();
        }
        if (time == 0 && allowsCoreThreadTimeOut()) {
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        }
        long keepAliveTime = unit.toNanos(time);
        long delta = keepAliveTime - this.keepAliveTime;
        this.keepAliveTime = keepAliveTime;
        if (delta < 0) {
            interruptIdleWorkers();
        }
    }

    /**
     * Returns the thread keep-alive time, which is the amount of time
     * that threads in excess of the core pool size may remain
     * idle before being terminated.
     *
     * @param unit the desired time unit of the result
     * @return the time limit
     * @see #setKeepAliveTime(long, TimeUnit)
     */
    public long getKeepAliveTime(TimeUnit unit) {
        return unit.convert(keepAliveTime, TimeUnit.NANOSECONDS);
    }

    /* User-level queue utilities */

    /**
     * Returns the task queue used by this executor. Access to the
     * task queue is intended primarily for debugging and monitoring.
     * This queue may be in active use.  Retrieving the task queue
     * does not prevent queued tasks from executing.
     *
     * @return the task queue
     */
    public BlockingQueue<Runnable> getQueue() {
        return workQueue;
    }

    /**
     * Removes this task from the executor's internal queue if it is
     * present, thus causing it not to be run if it has not already
     * started.(如果执行程序的内部队列中存在此任务，则将其删除，如果尚未启动，则导致该任务无法运行。)
     *
     * <p>This method may be useful as one part of a cancellation
     * scheme.  It may fail to remove tasks that have been converted
     * into other forms before being placed on the internal queue. For
     * example, a task entered using {@code submit} might be
     * converted into a form that maintains {@code Future} status.
     * However, in such cases, method {@link #purge} may be used to
     * remove those Futures that have been cancelled.
     *
     * @param task the task to remove
     * @return {@code true} if the task was removed
     */
    public boolean remove(Runnable task) {
        boolean removed = workQueue.remove(task);
        tryTerminate(); // In case SHUTDOWN and now empty
        return removed;
    }

    /**
     * Tries to remove from the work queue all {@link Future}
     * tasks that have been cancelled. This method can be useful as a
     * storage reclamation operation, that has no other impact on
     * functionality. Cancelled tasks are never executed, but may
     * accumulate in work queues until worker threads can actively
     * remove them. Invoking this method instead tries to remove them now.
     * However, this method may fail to remove tasks in
     * the presence of interference by other threads.
     *
     * 清除
     */
    public void purge() {
        final BlockingQueue<Runnable> q = workQueue;
        try {
            Iterator<Runnable> it = q.iterator();
            while (it.hasNext()) {
                Runnable r = it.next();
                if (r instanceof Future<?> && ((Future<?>) r).isCancelled()) {
                    it.remove();
                }
            }
        } catch (ConcurrentModificationException fallThrough) {
            // Take slow path if we encounter interference during traversal.
            // Make copy for traversal and call remove for cancelled entries.
            // The slow path is more likely to be O(N*N).
            for (Object r : q.toArray()) {
                if (r instanceof Future<?> && ((Future<?>) r).isCancelled()) {
                    q.remove(r);
                }
            }
        }

        tryTerminate(); // In case SHUTDOWN and now empty
    }

    /* Statistics */

    /**
     * Returns the current number of threads in the pool.
     *
     * @return the number of threads
     */
    public int getPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // Remove rare and surprising possibility of
            // isTerminated() && getPoolSize() > 0
            return runStateAtLeast(ctl.get(), TIDYING) ? 0
                    : workers.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate number of threads that are actively
     * executing tasks.
     *
     * @return the number of threads
     */
    public int getActiveCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            int n = 0;
            for (Worker w : workers) {
                if (w.isLocked()) {
                    ++n;
                }
            }
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the largest number of threads that have ever
     * simultaneously been in the pool.
     *
     * @return the number of threads
     */
    public int getLargestPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            return largestPoolSize;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate total number of tasks that have ever been
     * scheduled for execution. Because the states of tasks and
     * threads may change dynamically during computation, the returned
     * value is only an approximation.
     *
     * @return the number of tasks
     */
    public long getTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers) {
                n += w.completedTasks;
                if (w.isLocked()) {
                    ++n;
                }
            }
            return n + workQueue.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate total number of tasks that have
     * completed execution. Because the states of tasks and threads
     * may change dynamically during computation, the returned value
     * is only an approximation, but one that does not ever decrease
     * across successive calls.
     *
     * @return the number of tasks
     */
    public long getCompletedTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers) {
                n += w.completedTasks;
            }
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns a string identifying this pool, as well as its state,
     * including indications of run state and estimated worker and
     * task counts.
     *
     * @return a string identifying this pool, as well as its state
     */
    public String toString() {
        long ncompleted;
        int nworkers, nactive;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            ncompleted = completedTaskCount;
            nactive = 0;
            nworkers = workers.size();
            for (Worker w : workers) {
                ncompleted += w.completedTasks;
                if (w.isLocked()) {
                    ++nactive;
                }
            }
        } finally {
            mainLock.unlock();
        }
        int c = ctl.get();
        String rs = (runStateLessThan(c, SHUTDOWN) ? "Running" :
                (runStateAtLeast(c, TERMINATED) ? "Terminated" :
                        "Shutting down"));
        return super.toString() +
                "[" + rs +
                ", pool size = " + nworkers +
                ", active threads = " + nactive +
                ", queued tasks = " + workQueue.size() +
                ", completed tasks = " + ncompleted +
                "]";
    }

    /* Extension hooks */

    /**
     * Method invoked prior to executing the given Runnable in the
     * given thread.  This method is invoked by thread {@code t} that
     * will execute task {@code r}, and may be used to re-initialize
     * ThreadLocals, or to perform logging.
     *
     * <p>This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke {@code super.beforeExecute} at the end of
     * this method.
     *
     * @param t the thread that will run task {@code r}
     * @param r the task that will be executed
     */
    protected void beforeExecute(Thread t, Runnable r) {
    }

    /**
     * Method invoked upon completion of execution of the given Runnable.
     * This method is invoked by the thread that executed the task. If
     * non-null, the Throwable is the uncaught {@code RuntimeException}
     * or {@code Error} that caused execution to terminate abruptly.
     *
     * <p>This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke {@code super.afterExecute} at the
     * beginning of this method.
     *
     * <p><b>Note:</b> When actions are enclosed in tasks (such as
     * {@link FutureTask}) either explicitly or via methods such as
     * {@code submit}, these task objects catch and maintain
     * computational exceptions, and so they do not cause abrupt
     * termination, and the internal exceptions are <em>not</em>
     * passed to this method. If you would like to trap both kinds of
     * failures in this method, you can further probe for such cases,
     * as in this sample subclass that prints either the direct cause
     * or the underlying exception if a task has been aborted:
     *
     * <pre> {@code
     * class ExtendedExecutor extends ThreadPoolExecutor {
     *   // ...
     *   protected void afterExecute(Runnable r, Throwable t) {
     *     super.afterExecute(r, t);
     *     if (t == null && r instanceof Future<?>) {
     *       try {
     *         Object result = ((Future<?>) r).get();
     *       } catch (CancellationException ce) {
     *           t = ce;
     *       } catch (ExecutionException ee) {
     *           t = ee.getCause();
     *       } catch (InterruptedException ie) {
     *           Thread.currentThread().interrupt(); // ignore/reset
     *       }
     *     }
     *     if (t != null)
     *       System.out.println(t);
     *   }
     * }}</pre>
     *
     * @param r the runnable that has completed
     * @param t the exception that caused termination, or null if
     * execution completed normally
     */
    protected void afterExecute(Runnable r, Throwable t) {
    }

    /**
     * Method invoked when the Executor has terminated.  Default
     * implementation does nothing. Note: To properly nest multiple
     * overridings, subclasses should generally invoke
     * {@code super.terminated} within this method.
     */
    protected void terminated() {
    }

    /* Predefined RejectedExecutionHandlers */

    /**
     * A handler for rejected tasks that runs the rejected task
     * directly in the calling thread of the {@code execute} method,
     * unless the executor has been shut down, in which case the task
     * is discarded.
     */
    public static class CallerRunsPolicy implements RejectedExecutionHandler {

        /**
         * Creates a {@code CallerRunsPolicy}.
         */
        public CallerRunsPolicy() {
        }

        /**
         * Executes task r in the caller's thread, unless the executor
         * has been shut down, in which case the task is discarded.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        //        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        //            if (!e.isShutdown()) {
        //                r.run();
        //            }
        //        }
        @Override
        public void rejectedExecution(Runnable r, java.util.concurrent.ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                r.run();
            }
        }
    }

    /**
     * A handler for rejected tasks that throws a
     * {@code RejectedExecutionException}.
     */
    public static class AbortPolicy implements RejectedExecutionHandler {

        /**
         * Creates an {@code AbortPolicy}.
         */
        public AbortPolicy() {
        }

        /**
         * Always throws RejectedExecutionException.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         * @throws RejectedExecutionException always
         */
        //        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        //            throw new RejectedExecutionException("Task " + r.toString() +
        //                    " rejected from " +
        //                    e.toString());
        //        }
        @Override
        public void rejectedExecution(Runnable r, java.util.concurrent.ThreadPoolExecutor e) {
            throw new RejectedExecutionException("Task " + r.toString() + " rejected from " + e.toString());
        }
    }

    /**
     * A handler for rejected tasks that silently discards the
     * rejected task.
     */
    public static class DiscardPolicy implements RejectedExecutionHandler {

        /**
         * Creates a {@code DiscardPolicy}.
         */
        public DiscardPolicy() {
        }

        /**
         * Does nothing, which has the effect of discarding task r.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        //        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        //        }
        @Override
        public void rejectedExecution(Runnable r, java.util.concurrent.ThreadPoolExecutor executor) {

        }
    }

    /**
     * A handler for rejected tasks that discards the oldest unhandled
     * request and then retries {@code execute}, unless the executor
     * is shut down, in which case the task is discarded.
     */
    public static class DiscardOldestPolicy implements RejectedExecutionHandler {

        /**
         * Creates a {@code DiscardOldestPolicy} for the given executor.
         */
        public DiscardOldestPolicy() {
        }

        /**
         * Obtains and ignores the next task that the executor
         * would otherwise execute, if one is immediately available,
         * and then retries execution of task r, unless the executor
         * is shut down, in which case task r is instead discarded.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        //        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        //            if (!e.isShutdown()) {
        //                e.getQueue().poll();
        //                e.execute(r);
        //            }
        //        }
        @Override
        public void rejectedExecution(Runnable r, java.util.concurrent.ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                e.getQueue().poll();
                e.execute(r);
            }
        }
    }
}
