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

package com.spotify.scio

import com.spotify.scio.ContextAndArgs.{ArgsParser, PipelineOptionsParser, UsageOrHelpException}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Failure, Success, Try}
import org.apache.beam.sdk.options.PipelineOptions
import org.apache.beam.sdk.options.Validation.Required

class ArgsTest extends AnyFlatSpec with Matchers {
  "Args" should "support String" in {
    Args(Array("--str=value"))("str") shouldBe "value"
  }

  it should "support empty String" in {
    Args(Array("--str="))("str") shouldBe ""
  }

  it should "support getOrElse" in {
    Args(Array("--key1=value1"))
      .getOrElse("key2", "value2") shouldBe "value2"
  }

  it should "support list" in {
    Args(Array("--key=value1", "--key=value2"))
      .list("key") shouldBe List("value1", "value2")
  }

  it should "support optional" in {
    val args = Args(Array("--key1=value1"))
    args.optional("key1") shouldBe Some("value1")
    args.optional("key2") shouldBe None
  }

  it should "support required" in {
    Args(Array("--key=value")).required("key") shouldBe "value"
  }

  it should "fail required with missing value" in {
    the[IllegalArgumentException] thrownBy {
      Args(Array.empty).required("key")
    } should have message "Missing value for property 'key'"
  }

  it should "fail required with multiple values" in {
    the[IllegalArgumentException] thrownBy {
      Args(Array("--key=value1", "--key=value2")).required("key")
    } should have message "Multiple values for property 'key'"
  }

  it should "support int" in {
    val args = Args(Array("--key1=10"))
    args.int("key1") shouldBe 10
    args.int("key2", 20) shouldBe 20
  }

  it should "support long" in {
    val args = Args(Array("--key1=10"))
    args.long("key1") shouldBe 10L
    args.long("key2", 20L) shouldBe 20L
  }

  it should "support float" in {
    val args = Args(Array("--key1=1.5"))
    args.float("key1") shouldBe 1.5f
    args.float("key2", 2.5f) shouldBe 2.5f
  }

  it should "support double" in {
    val args = Args(Array("--key1=1.5"))
    args.double("key1") shouldBe 1.5
    args.double("key2", 2.5) shouldBe 2.5
  }

  it should "support boolean" in {
    val args = Args(Array("--key1=true", "--key2=false", "--key3"))
    args.boolean("key1") shouldBe true
    args.boolean("key2") shouldBe false
    args.boolean("key3") shouldBe true
    args.boolean("key4", true) shouldBe true
    args.boolean("key5", false) shouldBe false
  }

  it should "support quotes" in {
    def list(s: String): List[String] = Args(Array(s"--list=$s")).list("list")
    list("a,b,c") shouldBe List("a", "b", "c")
    list(",a,b") shouldBe List("", "a", "b")
    list("a,,b") shouldBe List("a", "", "b")
    list("a,b,") shouldBe List("a", "b", "")
    list("\"a1,a2\",b,c") shouldBe List("\"a1,a2\"", "b", "c")
    list("a,\"b1,b2\",c") shouldBe List("a", "\"b1,b2\"", "c")
    list("a,b,\"c1,c2\"") shouldBe List("a", "b", "\"c1,c2\"")
    list("a,\"b1, b2\",c") shouldBe List("a", "\"b1, b2\"", "c")
    list("a,b0 \"b1, b2\" b3,c") shouldBe List("a", "b0 \"b1, b2\" b3", "c")
  }

  it should "support toString" in {
    val args =
      Args(Array("--key1=value1", "--key2=value2", "--key2=value3", "--key3"))
    args.toString shouldBe "Args(--key1=value1, --key2=[value2, value3], --key3=true)"
  }

  trait Options extends PipelineOptions {
    @Required
    def getTestInput: String
    def setTestInput(input: String): Unit
    @Required
    def getTestOutput: String
    def setTestOutput(output: String): Unit
  }

  "PipelineOptionsParser" should "parse" in {
    val rawArgs = Array("--testInput=value1", "--testOutput=value2")
    val result = PipelineOptionsParser[Options]().parse(rawArgs)

    result should be a Symbol("success")
  }

  it should "fail on missing args" in {
    val rawArgs = Array("--testInput=value1")
    val result = PipelineOptionsParser[Options]().parse(rawArgs)

    result should be a Symbol("failure")
  }

  it should "fail on unused args" in {
    val rawArgs = Array("--testInput=value1", "--testOutput=value2", "--unused")
    val result = PipelineOptionsParser[Options]().parse(rawArgs)

    result should be a Symbol("failure")
  }

  "ContextAndArgs" should "rethrow parser exception" in {
    class FailingParserException extends Exception
    class FailingParser extends ArgsParser[Try] {
      override type ArgsType = Unit
      override def parse(args: Array[String]): Try[Result] = Failure(new FailingParserException)
    }
    assertThrows[FailingParserException] {
      ContextAndArgs.withParser(new FailingParser)(Array())
    }
  }

  it should "throw UsageOrHelpException on usage or help request" in {
    class UsageOrHelpParser extends ArgsParser[Try] {
      override type ArgsType = Unit
      override def parse(args: Array[String]): Try[Result] = Success(Left("This is usage message"))
    }
    assertThrows[UsageOrHelpException] {
      ContextAndArgs.withParser(new UsageOrHelpParser)(Array())
    }
  }
}
