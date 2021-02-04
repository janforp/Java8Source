package java.util.concurrent.locks;

/**
 * A synchronizer that may be exclusively owned by a thread.
 * -- 一个线程可能专有的同步器
 *
 * This class provides a basis for creating locks and related synchronizers that may entail a notion of ownership.
 * -- 此类提供了创建锁和相关的同步器（可能涉及所有权概念）的基础。
 *
 * The {@code AbstractOwnableSynchronizer} class itself does not manage or use this information.
 * -- {@code AbstractOwnableSynchronizer}类本身并不管理或使用此信息。
 *
 * However, subclasses and tools may use appropriately maintained values to help control and monitor access and provide diagnostics.
 * -- 但是，子类和工具可以使用适当维护的值来帮助控制和监视访问并提供诊断。
 *
 * @author Doug Lea
 * @since 1.6
 */
public abstract class AbstractOwnableSynchronizer implements java.io.Serializable {

    /**
     * Use serial ID even though all fields transient.
     */
    private static final long serialVersionUID = 3737899427754241961L;

    /**
     * Empty constructor for use by subclasses.
     */
    protected AbstractOwnableSynchronizer() {
    }

    /**
     * The current owner of exclusive mode synchronization.
     *
     * 专有所有者线程
     */
    private transient Thread exclusiveOwnerThread;

    /**
     * Sets the thread that currently owns exclusive access.
     * -- 设置当前拥有独占访问权的线程。
     *
     * A {@code null} argument indicates that no thread owns access.
     * -- {@code null}参数表示没有线程拥有访问权限。
     *
     * This method does not otherwise impose any synchronization or {@code volatile} field accesses.
     * -- 否则，此方法不会强加任何同步或{@code volatile}字段访问。
     *
     * @param thread the owner thread
     */
    protected final void setExclusiveOwnerThread(Thread thread) {
        exclusiveOwnerThread = thread;
    }

    /**
     * Returns the thread last set by {@code setExclusiveOwnerThread},
     * or {@code null} if never set.  This method does not otherwise
     * impose any synchronization or {@code volatile} field accesses.
     *
     * @return the owner thread
     */
    protected final Thread getExclusiveOwnerThread() {
        return exclusiveOwnerThread;
    }
}
