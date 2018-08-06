package com.ing.wbaa.gargoyle.proxy.data

import akka.http.scaladsl.model.{ HttpMethods, Uri }
import org.scalatest.{ DiagrammedAssertions, FlatSpec }

class S3RequestSpec extends FlatSpec with DiagrammedAssertions {

  val testCred = AwsRequestCredential(AwsAccessKey("ak"), Some(AwsSessionToken("st")))

  "S3Request" should "parse an S3 request from an http Path and Method" in {
    val result = S3Request(testCred, Uri.Path("/demobucket"), HttpMethods.GET)
    assert(result == S3Request(testCred, Some("demobucket"), Read))
  }

  it should "parse none for bucket if path is empty" in {
    val result = S3Request(testCred, Uri.Path("/"), HttpMethods.GET)
    assert(result == S3Request(testCred, None, Read))
  }

  it should "set access to write for anything but GET" in {
    val result = S3Request(testCred, Uri.Path("/demobucket"), HttpMethods.POST)
    assert(result == S3Request(testCred, Some("demobucket"), Write))
  }

}
