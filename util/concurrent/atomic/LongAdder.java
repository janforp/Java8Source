/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent.atomic;

import java.io.Serializable;

/**
 * One or more variables that together maintain an initially zero
 * {@code long} sum.  When updates (method {@link #add}) are contended
 * across threads, the set of variables may grow dynamically to reduce
 * contention. Method {@link #sum} (or, equivalently, {@link
 * #longValue}) returns the current total combined across the
 * variables maintaining the sum.
 *
 * <p>This class is usually preferable to {@link AtomicLong} when
 * multiple threads update a common sum that is used for purposes such
 * as collecting statistics, not for fine-grained synchronization
 * control.  Under low update contention, the two classes have similar
 * characteristics. But under high contention, expected throughput of
 * this class is significantly higher, at the expense of higher space
 * consumption.
 *
 * <p>LongAdders can be used with a {@link
 * java.util.concurrent.ConcurrentHashMap} to maintain a scalable
 * frequency map (a form of histogram or multiset). For example, to
 * add a count to a {@code ConcurrentHashMap<String,LongAdder> freqs},
 * initializing if not already present, you can use {@code
 * freqs.computeIfAbsent(k -> new LongAdder()).increment();}
 *
 * <p>This class extends {@link Number}, but does <em>not</em> define
 * methods such as {@code equals}, {@code hashCode} and {@code
 * compareTo} because instances are expected to be mutated, and so are
 * not useful as collection keys.
 *
 * @author Doug Lea
 * @since 1.8
 */
public class LongAdder extends Striped64 implements Serializable {

    private static final long serialVersionUID = 7249069246863182397L;

    /**
     * Creates a new adder with initial sum of zero.
     */
    public LongAdder() {
    }

    /**
     * Adds the given value.
     *
     * @param addValue the value to add
     */
    public void add(long addValue) {
        //表示cells的引用
        Cell[] asellsReference;
        //currentBaseValue：获取的base值
        //v：期望值
        long currentBaseValue, v;
        //表示cells数组的长度
        int lenOfCells;
        //表示当前线程命中的cell单元格
        Cell hitCell;

        //条件1：true->表示cells已经初始化过了，当前线程应该将数据写入到对应的cell中
        //条件1：false -> 表示cells未初始化，当前线程应该将数据写到base中
        if ((asellsReference = cells) != null//条件1，给as赋值，cells数组不为空
                ||
                //casBase 表示把值添加到base中，成功返回true，失败返回false
                //条件2：true->表示当前线程cas替换数据成功
                //条件2：false->表示发生了竞争，可能需要重试 或者 扩容
                //条件2，给b赋值为base，并且试图使用cas，但是没有成功则进入if代码块，否则此处就add成功了
                !casBase(currentBaseValue = base, currentBaseValue + addValue)) {

            //***********************什么时候能进来？*****************************
            //条件1为true->表示cells已经初始化过了，当前线程应该将数据写入到对应的cell中
            //条件2为false->casBase修改失败了，表示发生了竞争，可能需要重试 或者 扩容

            //true:没有竞争，false：有竞争
            boolean uncontended = true;

            //条件1，2：true：说明cells未初始化，也就是多线程写base发生竞争了
            //条件1，2：false：说明cells已经初始化了，当前线程应该是找到自己的cell 写值
            //条件1，as == null，说明cells未初始化
            //条件2，给m赋值为as的长度-1，如果m < 0 ,说明cells未初始化
            //总之就是cells还没有初始化
            if (asellsReference == null || (lenOfCells = asellsReference.length - 1) < 0
                    ||
                    //getProbe():获取当前线程的hash值，m表示cells长度-1，cells的长度一定为2的次方数，此处的逻辑就类似hashMap
                    //就相当于在cells数组中取模查询到该线程在cells数组中的下标
                    //条件3为true:说明当前线程对应下标的cell为空，需要创建cell（longAccumulate中会创建），
                    //条件3为false:说明当前线程对应的cell不为空，已经创建了，说明下一步想要将x值添加到cell中
                    //总之该步骤就是定位该线程的cell
                    (hitCell = asellsReference[getProbe() & lenOfCells]) == null
                    ||
                    //到这一步，说明当前线程的cell已经初始化了，就试图把数据写入该cell中，如果写入成功，则整体成功，如果写入失败，说明在该cell上发生了竞争
                    //条件4为true：表示cas失败，意味着当前线程对应的cell有竞争
                    //条件4为false：表示cas成功，就返回了
                    !(uncontended = hitCell.cas(v = hitCell.value, v + addValue))) {

                //什么时候进来呢？
                //条件1，2：true：说明cells未初始化，也就是多线程写base发生竞争了[猜测：重试，初始化cells数组]
                //条件3为true:说明当前线程对应下标的cell为空，需要创建cell（longAccumulate中会创建）[猜测：创建]
                //条件4为true：表示cas失败，意味着当前线程对应的cell有竞争[猜测：重试或者扩容]
                longAccumulate(addValue, null, uncontended);
            }
        }
    }

    /**
     * Equivalent to {@code add(1)}.
     */
    public void increment() {
        add(1L);
    }

    /**
     * Equivalent to {@code add(-1)}.
     */
    public void decrement() {
        add(-1L);
    }

    /**
     * Returns the current sum.  The returned value is <em>NOT</em> an
     * atomic snapshot; invocation in the absence of concurrent
     * updates returns an accurate result, but concurrent updates that
     * occur while the sum is being calculated might not be
     * incorporated.
     *
     * @return the sum
     */
    public long sum() {
        Cell[] as = cells;
        Cell a;
        long sum = base;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[i]) != null) {
                    sum += a.value;
                }
            }
        }
        return sum;
    }

    /**
     * Resets variables maintaining the sum to zero.  This method may
     * be a useful alternative to creating a new adder, but is only
     * effective if there are no concurrent updates.  Because this
     * method is intrinsically racy, it should only be used when it is
     * known that no threads are concurrently updating.
     */
    public void reset() {
        Cell[] as = cells;
        Cell a;
        base = 0L;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[i]) != null) {
                    a.value = 0L;
                }
            }
        }
    }

    /**
     * Equivalent in effect to {@link #sum} followed by {@link
     * #reset}. This method may apply for example during quiescent
     * points between multithreaded computations.  If there are
     * updates concurrent with this method, the returned value is
     * <em>not</em> guaranteed to be the final value occurring before
     * the reset.
     *
     * @return the sum
     */
    public long sumThenReset() {
        Cell[] as = cells;
        Cell a;
        long sum = base;
        base = 0L;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[i]) != null) {
                    sum += a.value;
                    a.value = 0L;
                }
            }
        }
        return sum;
    }

    /**
     * Returns the String representation of the {@link #sum}.
     *
     * @return the String representation of the {@link #sum}
     */
    public String toString() {
        return Long.toString(sum());
    }

    /**
     * Equivalent to {@link #sum}.
     *
     * @return the sum
     */
    public long longValue() {
        return sum();
    }

    /**
     * Returns the {@link #sum} as an {@code int} after a narrowing
     * primitive conversion.
     */
    public int intValue() {
        return (int) sum();
    }

    /**
     * Returns the {@link #sum} as a {@code float}
     * after a widening primitive conversion.
     */
    public float floatValue() {
        return (float) sum();
    }

    /**
     * Returns the {@link #sum} as a {@code double} after a widening
     * primitive conversion.
     */
    public double doubleValue() {
        return (double) sum();
    }

    /**
     * Serialization proxy, used to avoid reference to the non-public
     * Striped64 superclass in serialized forms.
     *
     * @serial include
     */
    private static class SerializationProxy implements Serializable {

        private static final long serialVersionUID = 7249069246863182397L;

        /**
         * The current value returned by sum().
         *
         * @serial
         */
        private final long value;

        SerializationProxy(LongAdder a) {
            value = a.sum();
        }

        /**
         * Return a {@code LongAdder} object with initial state
         * held by this proxy.
         *
         * @return a {@code LongAdder} object with initial state
         * held by this proxy.
         */
        private Object readResolve() {
            LongAdder a = new LongAdder();
            a.base = value;
            return a;
        }
    }

    /**
     * Returns a
     * <a href="../../../../serialized-form.html#java.util.concurrent.atomic.LongAdder.SerializationProxy">
     * SerializationProxy</a>
     * representing the state of this instance.
     *
     * @return a {@link SerializationProxy}
     * representing the state of this instance
     */
    private Object writeReplace() {
        return new SerializationProxy(this);
    }

    /**
     * @param s the stream
     * @throws java.io.InvalidObjectException always
     */
    private void readObject(java.io.ObjectInputStream s)
            throws java.io.InvalidObjectException {
        throw new java.io.InvalidObjectException("Proxy required");
    }

}
