package org.apache.lucene.search;

/*
 *
 * Copyright(c) 2015, Samsung Electronics Co., Ltd.
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the <organization> nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.
    
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.io.IOException;

/**
 * This abstract class defines methods to iterate over a set of non-decreasing
 * doc ids. Note that this class assumes it iterates on doc Ids, and therefore
 * {@link #NO_MORE_DOCS} is set to {@value #NO_MORE_DOCS} in order to be used as
 * a sentinel object. Implementations of this class are expected to consider
 * {@link Integer#MAX_VALUE} as an invalid value.
 */
public abstract class DocIdSetIterator {

    /** An empty {@code DocIdSetIterator} instance */
    public static final DocIdSetIterator empty() {
        return new DocIdSetIterator() {
            boolean exhausted = false;

            @Override
            public int advance(int target) {
                assert !exhausted;
                assert target >= 0;
                exhausted = true;
                return NO_MORE_DOCS;
            }

            @Override
            public int docID() {
                return exhausted ? NO_MORE_DOCS : -1;
            }
            @Override
            public int nextDoc() {
                assert !exhausted;
                exhausted = true;
                return NO_MORE_DOCS;
            }

            @Override
            public long cost() {
                return 0;
            }
        };
    }

    /**
     * When returned by {@link #nextDoc()}, {@link #advance(int)} and
     * {@link #docID()} it means there are no more docs in the iterator.
     */
    public static final int NO_MORE_DOCS = Integer.MAX_VALUE;

    /**
     * Returns the following:
     * <ul>
     * <li><code>-1</code> if {@link #nextDoc()} or
     * {@link #advance(int)} were not called yet.
     * <li>{@link #NO_MORE_DOCS} if the iterator has exhausted.
     * <li>Otherwise it should return the doc ID it is currently on.
     * </ul>
     * <p>
     *
     * @since 2.9
     */
    public abstract int docID();

    /**
     * Advances to the next document in the set and returns the doc it is
     * currently on, or {@link #NO_MORE_DOCS} if there are no more docs in the
     * set.<br>
     *
     * <b>NOTE:</b> after the iterator has exhausted you should not call this
     * method, as it may result in unpredicted behavior.
     *
     * @since 2.9
     */
    public abstract int nextDoc() throws IOException;

    /**
     * Advances to the first beyond the current whose document number is greater
     * than or equal to <i>target</i>, and returns the document number itself.
     * Exhausts the iterator and returns {@link #NO_MORE_DOCS} if <i>target</i>
     * is greater than the highest document number in the set.
     * <p>
     * The behavior of this method is <b>undefined</b> when called with
     * <code> target &le; current</code>, or after the iterator has exhausted.
     * Both cases may result in unpredicted behavior.
     * <p>
     * When <code> target &gt; current</code> it behaves as if written:
     *
     * <pre class="prettyprint">
     * int advance(int target) {
     *   int doc;
     *   while ((doc = nextDoc()) &lt; target) {
     *   }
     *   return doc;
     * }
     * </pre>
     *
     * Some implementations are considerably more efficient than that.
     * <p>
     * <b>NOTE:</b> this method may be called with {@link #NO_MORE_DOCS} for
     * efficiency by some Scorers. If your implementation cannot efficiently
     * determine that it should exhaust, it is recommended that you check for that
     * value in each call to this method.
     * <p>
     *
     * @since 2.9
     */
    public abstract int advance(int target) throws IOException;

    /** Slow (linear) implementation of {@link #advance} relying on
     *  {@link #nextDoc()} to advance beyond the target position. */
    protected final int slowAdvance(int target) throws IOException {
        assert docID() < target;
        int doc;
        do {
            doc = nextDoc();
        } while (doc < target);
        return doc;
    }

    /**
     * Returns the estimated cost of this {@link DocIdSetIterator}.
     * <p>
     * This is generally an upper bound of the number of documents this iterator
     * might match, but may be a rough heuristic, hardcoded value, or otherwise
     * completely inaccurate.
     */
    public abstract long cost();
}