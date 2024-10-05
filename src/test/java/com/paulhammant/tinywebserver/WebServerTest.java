package com.paulhammant.tinywebserver;

import org.forgerock.cuppa.Runner;
import org.forgerock.cuppa.Test;
import org.forgerock.cuppa.model.TestBlock;
import org.forgerock.cuppa.reporters.DefaultReporter;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;
import static org.hamcrest.MatcherAssert.assertThat;

@Test
public class WebServerTest {
    {
        describe("List", () -> {
            describe("#indexOf", () -> {
                it("returns -1 when the value is not present", () -> {
                    List<Integer> list = Arrays.asList(1, 2, 3);
                    assertThat(list.indexOf(5), Matchers.equalTo(-1));
                });
            });
        });
    }

    public static void main(String[] args) {
        Runner runner = new Runner();
        TestBlock rootBlock = runner.defineTests(Collections.singletonList(WebServerTest.class));
        runner.run(rootBlock, new DefaultReporter());
    }
}
