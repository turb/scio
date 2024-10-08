/*
 * Copyright 2019 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.scio.io.dynamic.syntax

import com.spotify.scio.io.{ClosedTap, EmptyTap, TextIO}
import com.spotify.scio.util.{Functions, ScioUtil}
import com.spotify.scio.values.SCollection
import org.apache.beam.sdk.coders.StringUtf8Coder
import org.apache.beam.sdk.io.{Compression, FileIO}
import org.apache.beam.sdk.{io => beam}

import scala.reflect.ClassTag
import scala.util.chaining._

object DynamicSCollectionOps {
  private[scio] def writeDynamic[A](
    path: String,
    destinationFn: A => String,
    numShards: Int,
    prefix: String,
    suffix: String,
    tempDirectory: String
  ): FileIO.Write[String, A] = {
    val naming = ScioUtil.defaultNaming(Option(prefix).getOrElse("part"), suffix) _
    FileIO
      .writeDynamic[String, A]()
      .to(path)
      .by(Functions.serializableFn(destinationFn))
      .withNumShards(numShards)
      .withDestinationCoder(StringUtf8Coder.of())
      .withNaming(Functions.serializableFn(naming))
      .pipe(t => Option(tempDirectory).fold(t)(t.withTempDirectory))
  }
}

/**
 * Enhanced version of [[com.spotify.scio.values.SCollection SCollection]] with dynamic destinations
 * methods.
 */
final class DynamicSCollectionOps[T](private val self: SCollection[T]) extends AnyVal {
  import DynamicSCollectionOps.writeDynamic

  /** Save this SCollection as text files specified by the destination function. */
  def saveAsDynamicTextFile(
    path: String,
    numShards: Int = TextIO.WriteParam.DefaultNumShards,
    suffix: String = TextIO.WriteParam.DefaultSuffix,
    compression: Compression = TextIO.WriteParam.DefaultCompression,
    tempDirectory: String = TextIO.WriteParam.DefaultTempDirectory,
    prefix: String = TextIO.WriteParam.DefaultPrefix,
    header: Option[String] = TextIO.WriteParam.DefaultHeader,
    footer: Option[String] = TextIO.WriteParam.DefaultFooter
  )(destinationFn: String => String)(implicit ct: ClassTag[T]): ClosedTap[Nothing] = {
    val s = if (classOf[String] isAssignableFrom ct.runtimeClass) {
      self.asInstanceOf[SCollection[String]]
    } else {
      self.map(_.toString)
    }
    if (self.context.isTest) {
      throw new NotImplementedError(
        "Text file with dynamic destinations cannot be used in a test context"
      )
    } else {
      val sink = beam.TextIO
        .sink()
        .pipe(s => header.fold(s)(s.withHeader))
        .pipe(s => footer.fold(s)(s.withFooter))

      val write = writeDynamic(
        path = path,
        destinationFn = destinationFn,
        numShards = numShards,
        prefix = prefix,
        suffix = suffix,
        tempDirectory = tempDirectory
      ).via(sink)
        .withCompression(compression)
      s.applyInternal(write)
    }

    ClosedTap[Nothing](EmptyTap)
  }
}

trait SCollectionSyntax {
  implicit def dynamicSCollectionOps[T](sc: SCollection[T]): DynamicSCollectionOps[T] =
    new DynamicSCollectionOps(sc)
}
