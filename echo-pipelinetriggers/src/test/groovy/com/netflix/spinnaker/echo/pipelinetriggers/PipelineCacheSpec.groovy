/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.netflix.spinnaker.echo.pipelinetriggers

import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.services.Front50Service
import com.netflix.spinnaker.echo.test.RetrofitStubs
import rx.schedulers.Schedulers
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static java.util.concurrent.TimeUnit.SECONDS
import static rx.Observable.empty
import static rx.Observable.just

class PipelineCacheSpec extends Specification implements RetrofitStubs {
  def scheduler = Schedulers.test()
  def front50 = Mock(Front50Service)
  def registry = Stub(Registry) {
    createId(*_) >> Stub(Id)
    counter(*_) >> Stub(Counter)
    gauge(*_) >> Integer.valueOf(1)
  }


  @Shared
  def interval = 30

  @Subject
  def pipelineCache = new PipelineCache(scheduler, interval, front50, registry)

  private waitForTicks(int n) {
    scheduler.advanceTimeBy(n * interval, SECONDS)
  }

  def "doesn't poll until started"() {
    when:
    waitForTicks(1)

    then:
    0 * _
  }

  def "doesn't poll after being stopped"() {
    given:
    pipelineCache.start()
    pipelineCache.stop()

    when:
    waitForTicks(1)

    then:
    0 * _
  }

  def "tolerates stop before start"() {
    when:
    pipelineCache.stop()

    then:
    notThrown Throwable
  }

  def "tolerates multiple stop calls"() {
    given:
    pipelineCache.start()
    pipelineCache.stop()

    when:
    pipelineCache.stop()

    then:
    notThrown Throwable
  }

  def "tolerates multiple start calls"() {
    given:
    pipelineCache.start()
    pipelineCache.start()
    pipelineCache.stop()

    when:
    waitForTicks(1)

    then:
    0 * _
  }

  def "can be restarted after shutting down"() {
    given:
    pipelineCache.start()
    pipelineCache.stop()
    pipelineCache.start()

    when:
    waitForTicks(1)

    then:
    1 * front50.getPipelines() >> empty()
  }

  @Unroll
  def "polls Front50 #ticks times in #delayTime seconds"() {
    given:
    pipelineCache.start()

    when:
    waitForTicks(ticks)

    then:
    ticks * front50.getPipelines() >> empty()

    where:
    ticks | _
    1     | _
    2     | _

    delayTime = ticks * interval
  }

  def "keeps polling if Front50 returns an error"() {
    given:
    def pipeline = Pipeline.builder().application('application').name('Pipeline').id('P1').build()
    pipelineCache.start()

    when:
    waitForTicks(3)

    then:
    front50.getPipelines() >> just([]) >> { throw unavailable() } >> just([pipeline])
    pipelineCache.pipelines == [pipeline]
  }
}
