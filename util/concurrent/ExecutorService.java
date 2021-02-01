package java.util.concurrent;

import java.util.Collection;
import java.util.List;

/**
 * An {@link Executor} that provides methods to manage termination and
 * methods that can produce a {@link Future} for tracking progress of
 * one or more asynchronous tasks.
 *
 * -- 一种Executor，提供管理终止的方法以及可以产生Future来跟踪一个或多个异步任务进度的方法。
 *
 * <p>An {@code ExecutorService} can be shut down, which will cause it to reject new tasks.
 * -- 可以关闭{@code ExecutorService}，这将导致它拒绝新任务。
 *
 * Two different methods are provided for shutting down an {@code ExecutorService}.
 *
 * The {@link #shutdown} method will allow previously submitted tasks to execute before terminating,
 * -- shutdown 方法将允许先前提交的任务在终止之前执行, 也就是说已经提交的任务可以执行完成
 *
 * while the {@link #shutdownNow} method prevents waiting tasks from starting and attempts to stop currently executing tasks.
 * -- 而{@link #shutdownNow}方法可阻止等待的任务启动并尝试停止(发送一个中断信号)当前正在执行的任务。
 *
 *
 * Upon termination, an executor has no tasks actively executing, no tasks awaiting execution, and no new tasks can be submitted.
 *
 * -- 终止后，执行者将没有正在执行的任务，没有正在等待执行的任务，并且无法提交新任务。
 *
 * An unused {@code ExecutorService} should be shut down to allow reclamation of its resources.
 * -- 应该关闭未使用的{@code ExecutorService}以便回收其资源。
 *
 * <p>Method {@code submit} extends base method {@link Executor#execute(Runnable)} by creating and returning a {@link Future} that can be used to cancel execution and/or wait for completion.
 * -- submit方法通过创建并返回一个可用于取消执行和/或等待完成的{@link Future}来扩展基本方法{@link Executor＃execute（Runnable）}。
 *
 * Methods {@code invokeAny} and {@code invokeAll} perform the most commonly useful forms of bulk execution, executing a collection of tasks and then waiting for at least one, or all, to complete.
 * -- 方法{@code invokeAny}和{@code invokeAll}执行批量执行的最常用形式，执行一组任务，然后等待至少一个或全部完成。
 *
 * (Class {@link ExecutorCompletionService} can be used to write customized variants of these methods.)
 *
 * <p>The {@link Executors} class provides factory methods for the executor services provided in this package.
 * -- {@link Executors}类为此程序包中提供的执行程序服务提供了工厂方法。
 *
 * <h3>Usage Examples</h3>
 *
 * Here is a sketch of a network service in which threads in a thread pool service incoming requests.
 * -- 这是网络服务的示意图，其中线程池中的线程服务传入的请求。
 *
 * It uses the preconfigured {@link Executors#newFixedThreadPool} factory method:
 *
 * <pre> {@code
 * class NetworkService implements Runnable {
 *   private final ServerSocket serverSocket;
 *   private final ExecutorService pool;
 *
 *   public NetworkService(int port, int poolSize)
 *       throws IOException {
 *     serverSocket = new ServerSocket(port);
 *     pool = Executors.newFixedThreadPool(poolSize);
 *   }
 *
 *   public void run() { // run the service
 *     try {
 *       for (;;) {
 *         pool.execute(new Handler(serverSocket.accept()));
 *       }
 *     } catch (IOException ex) {
 *       pool.shutdown();
 *     }
 *   }
 * }
 *
 * class Handler implements Runnable {
 *   private final Socket socket;
 *   Handler(Socket socket) { this.socket = socket; }
 *   public void run() {
 *     // read and service request on socket
 *   }
 * }}</pre>
 *
 * The following method shuts down an {@code ExecutorService} in two phases, first by calling {@code shutdown} to reject incoming tasks, and then calling {@code shutdownNow}, if necessary, to cancel any lingering tasks:
 * -- 以下方法分两个阶段关闭{@code ExecutorService}，首先是通过调用{@code shutdown}拒绝传入的任务，然后在必要时调用{@code shutdownNow}来取消所有未完成的任务：
 *
 * <pre> {@code
 * void shutdownAndAwaitTermination(ExecutorService pool) {
 *   pool.shutdown(); // Disable new tasks from being submitted
 *   try {
 *     // Wait a while for existing tasks to terminate
 *     if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
 *       pool.shutdownNow(); // Cancel currently executing tasks
 *       // Wait a while for tasks to respond to being cancelled
 *       if (!pool.awaitTermination(60, TimeUnit.SECONDS))
 *           System.err.println("Pool did not terminate");
 *     }
 *   } catch (InterruptedException ie) {
 *     // (Re-)Cancel if current thread also interrupted
 *     pool.shutdownNow();
 *     // Preserve interrupt status
 *     Thread.currentThread().interrupt();
 *   }
 * }}</pre>
 *
 * <p>Memory consistency effects: Actions in a thread prior to the
 * submission of a {@code Runnable} or {@code Callable} task to an
 * {@code ExecutorService}
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * any actions taken by that task, which in turn <i>happen-before</i> the
 * result is retrieved via {@code Future.get()}.
 *
 * @author Doug Lea
 * @since 1.5
 */
public interface ExecutorService extends Executor {

    /**
     * Initiates an orderly shutdown in which previously submitted tasks are executed, but no new tasks will be accepted.
     * -- 启动有序关闭，在该关闭中执行先前提交的任务，但不接受任何新任务。
     *
     * Invocation has no additional effect if already shut down.
     * -- 如果已经关闭，则调用不会产生任何其他影响。
     *
     * This method does not wait for previously submitted tasks to complete execution.
     * -- 此方法不等待先前提交的任务完成执行。
     *
     * Use {@link #awaitTermination awaitTermination} to do that.
     *
     * @throws SecurityException if a security manager exists and
     * shutting down this ExecutorService may manipulate
     * threads that the caller is not permitted to modify
     * because it does not hold {@link
     * java.lang.RuntimePermission}{@code ("modifyThread")},
     * or the security manager's {@code checkAccess} method
     * denies access.
     */
    void shutdown();

    /**
     * Attempts to stop all actively executing tasks, halts the processing of waiting tasks, and returns a list of the tasks that were awaiting execution.
     * -- 尝试停止所有正在执行的任务，暂停正在等待的任务的处理，并返回正在等待执行的任务的列表.
     *
     * <p>This method does not wait for actively executing tasks to
     * terminate.  Use {@link #awaitTermination awaitTermination} to
     * do that.
     *
     * There are no guarantees beyond best-effort attempts to stop processing actively executing tasks.
     * For example, typical implementations will cancel via {@link Thread#interrupt}, so any task that fails to respond to interrupts may never terminate.
     * -- 除了尽最大努力停止处理正在执行的任务之外，没有任何保证。例如，典型的实现将通过{@link Thread＃interrupt}取消，因此任何无法响应中断的任务都可能永远不会终止。
     * -- 也就是尽力而为，例如发送一个中断信号,具体要看正在执行的任务是否是一个响应中断的任务来
     *
     * @return list of tasks that never commenced execution
     * @throws SecurityException if a security manager exists and
     * shutting down this ExecutorService may manipulate
     * threads that the caller is not permitted to modify
     * because it does not hold {@link
     * java.lang.RuntimePermission}{@code ("modifyThread")},
     * or the security manager's {@code checkAccess} method
     * denies access.
     */
    List<Runnable> shutdownNow();

    /**
     * Returns {@code true} if this executor has been shut down.
     *
     * @return {@code true} if this executor has been shut down
     */
    boolean isShutdown();

    /**
     * Returns {@code true} if all tasks have completed following shut down.
     * Note that {@code isTerminated} is never {@code true} unless
     * either {@code shutdown} or {@code shutdownNow} was called first.
     *
     * @return {@code true} if all tasks have completed following shut down
     */
    boolean isTerminated();

    /**
     * Blocks until all tasks have completed execution after a shutdown
     * request, or the timeout occurs, or the current thread is
     * interrupted, whichever happens first.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return {@code true} if this executor terminated and
     * {@code false} if the timeout elapsed before termination
     * @throws InterruptedException if interrupted while waiting
     */
    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Submits a value-returning task for execution and returns a
     * Future representing the pending results of the task. The
     * Future's {@code get} method will return the task's result upon
     * successful completion.
     *
     * <p>
     * If you would like to immediately block waiting
     * for a task, you can use constructions of the form
     * {@code result = exec.submit(aCallable).get();}
     *
     * <p>Note: The {@link Executors} class includes a set of methods
     * that can convert some other common closure-like objects,
     * for example, {@link java.security.PrivilegedAction} to
     * {@link Callable} form so they can be submitted.
     *
     * @param task the task to submit
     * @param <T> the type of the task's result
     * @return a Future representing pending completion of the task
     * @throws RejectedExecutionException if the task cannot be
     * scheduled for execution
     * @throws NullPointerException if the task is null
     */
    <T> Future<T> submit(Callable<T> task);

    /**
     * Submits a Runnable task for execution and returns a Future
     * representing that task. The Future's {@code get} method will
     * return the given result upon successful completion.
     *
     * @param task the task to submit
     * @param result the result to return
     * @param <T> the type of the result
     * @return a Future representing pending completion of the task
     * @throws RejectedExecutionException if the task cannot be
     * scheduled for execution
     * @throws NullPointerException if the task is null
     */
    <T> Future<T> submit(Runnable task, T result);

    /**
     * Submits a Runnable task for execution and returns a Future
     * representing that task. The Future's {@code get} method will
     * return {@code null} upon <em>successful</em> completion.
     *
     * @param task the task to submit
     * @return a Future representing pending completion of the task
     * @throws RejectedExecutionException if the task cannot be
     * scheduled for execution
     * @throws NullPointerException if the task is null
     */
    Future<?> submit(Runnable task);

    /**
     * Executes the given tasks, returning a list of Futures holding
     * their status and results when all complete.
     * {@link Future#isDone} is {@code true} for each
     * element of the returned list.
     * Note that a <em>completed</em> task could have
     * terminated either normally or by throwing an exception.
     * The results of this method are undefined if the given
     * collection is modified while this operation is in progress.
     *
     * @param tasks the collection of tasks
     * @param <T> the type of the values returned from the tasks
     * @return a list of Futures representing the tasks, in the same
     * sequential order as produced by the iterator for the
     * given task list, each of which has completed
     * @throws InterruptedException if interrupted while waiting, in
     * which case unfinished tasks are cancelled
     * @throws NullPointerException if tasks or any of its elements are {@code null}
     * @throws RejectedExecutionException if any task cannot be
     * scheduled for execution
     */
    <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException;

    /**
     * Executes the given tasks, returning a list of Futures holding
     * their status and results
     * when all complete or the timeout expires, whichever happens first.
     * {@link Future#isDone} is {@code true} for each
     * element of the returned list.
     * Upon return, tasks that have not completed are cancelled.
     * Note that a <em>completed</em> task could have
     * terminated either normally or by throwing an exception.
     * The results of this method are undefined if the given
     * collection is modified while this operation is in progress.
     *
     * @param tasks the collection of tasks
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @param <T> the type of the values returned from the tasks
     * @return a list of Futures representing the tasks, in the same
     * sequential order as produced by the iterator for the
     * given task list. If the operation did not time out,
     * each task will have completed. If it did time out, some
     * of these tasks will not have completed.
     * @throws InterruptedException if interrupted while waiting, in
     * which case unfinished tasks are cancelled
     * @throws NullPointerException if tasks, any of its elements, or
     * unit are {@code null}
     * @throws RejectedExecutionException if any task cannot be scheduled
     * for execution
     */
    <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
            long timeout, TimeUnit unit)
            throws InterruptedException;

    /**
     * Executes the given tasks, returning the result
     * of one that has completed successfully (i.e., without throwing
     * an exception), if any do. Upon normal or exceptional return,
     * tasks that have not completed are cancelled.
     * The results of this method are undefined if the given
     * collection is modified while this operation is in progress.
     *
     * @param tasks the collection of tasks
     * @param <T> the type of the values returned from the tasks
     * @return the result returned by one of the tasks
     * @throws InterruptedException if interrupted while waiting
     * @throws NullPointerException if tasks or any element task
     * subject to execution is {@code null}
     * @throws IllegalArgumentException if tasks is empty
     * @throws ExecutionException if no task successfully completes
     * @throws RejectedExecutionException if tasks cannot be scheduled
     * for execution
     */
    <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException;

    /**
     * Executes the given tasks, returning the result
     * of one that has completed successfully (i.e., without throwing
     * an exception), if any do before the given timeout elapses.
     * Upon normal or exceptional return, tasks that have not
     * completed are cancelled.
     * The results of this method are undefined if the given
     * collection is modified while this operation is in progress.
     *
     * @param tasks the collection of tasks
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @param <T> the type of the values returned from the tasks
     * @return the result returned by one of the tasks
     * @throws InterruptedException if interrupted while waiting
     * @throws NullPointerException if tasks, or unit, or any element
     * task subject to execution is {@code null}
     * @throws TimeoutException if the given timeout elapses before
     * any task successfully completes
     * @throws ExecutionException if no task successfully completes
     * @throws RejectedExecutionException if tasks cannot be scheduled
     * for execution
     */
    <T> T invokeAny(Collection<? extends Callable<T>> tasks,
            long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException;
}
