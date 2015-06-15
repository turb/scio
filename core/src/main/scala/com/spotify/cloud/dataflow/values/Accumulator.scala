package com.spotify.cloud.dataflow.values

import com.google.cloud.dataflow.sdk.transforms.Combine.CombineFn
import com.google.cloud.dataflow.sdk.transforms.Max.{MaxDoubleFn, MaxIntegerFn, MaxLongFn}
import com.google.cloud.dataflow.sdk.transforms.Min.{MinDoubleFn, MinIntegerFn, MinLongFn}
import com.google.cloud.dataflow.sdk.transforms.Sum.{SumDoubleFn, SumIntegerFn, SumLongFn}
import com.google.cloud.dataflow.sdk.transforms.{Aggregator, Combine}

/** Type class for `T` that can be used in an [[Accumulator]]. */
sealed trait AccumulatorType[T] {
  type CF = CombineFn[T, Array[T], T]
  type BCF = Combine.BinaryCombineFn[T]

  protected def sumFnImpl(): CombineFn[_, _, _]
  protected def minFnImpl(): Combine.BinaryCombineFn[_]
  protected def maxFnImpl(): Combine.BinaryCombineFn[_]

  /** CombineFn for computing sum of the underlying values. */
  def sumFn(): CF = sumFnImpl().asInstanceOf[CF]

  /** BinaryCombineFn for computing maximum of the underlying values. */
  def minFn(): BCF = minFnImpl().asInstanceOf[BCF]

  /** BinaryCombineFn for computing minimum of the underlying values. */
  def maxFn(): BCF = maxFnImpl().asInstanceOf[BCF]
}

private[dataflow] class IntAccumulatorType extends AccumulatorType[Int] {
  override protected def sumFnImpl() = new SumIntegerFn()
  override protected def minFnImpl() = new MinIntegerFn()
  override protected def maxFnImpl() = new MaxIntegerFn()
}

private[dataflow] class LongAccumulatorType extends AccumulatorType[Long] {
  override protected def sumFnImpl() = new SumLongFn()
  override protected def minFnImpl() = new MinLongFn()
  override protected def maxFnImpl() = new MaxLongFn()
}

private[dataflow] class DoubleAccumulatorType extends AccumulatorType[Double] {
  override protected def sumFnImpl() =  new SumDoubleFn()
  override protected def minFnImpl() = new MinDoubleFn()
  override protected def maxFnImpl() = new MaxDoubleFn()
}

/** Encapsulate an accumulator, similar to Hadoop counters. */
trait Accumulator[T] extends Serializable {

  private[dataflow] val combineFn: CombineFn[T, _, T]

  private[dataflow] val name: String

}

/** Encapsulate context of one or more [[Accumulator]]s in an [[SCollectionWithAccumulator]]. */
class AccumulatorContext private[dataflow] (private val m: Map[String, Aggregator[_, _]]) extends AnyVal {

  /** Add a value to the given [[Accumulator]]. */
  def addValue[T](acc: Accumulator[T], value: T): AccumulatorContext = {
    m(acc.name).asInstanceOf[Aggregator[T, T]].addValue(value)
    this
  }

}
