/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hopper.presentation.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hopper.core.HColumn;
import org.hopper.core.draw.DrawnContext;
import org.junit.jupiter.api.Test;

class HInteractionActionLinkPlaceholderTest {

  @Test
  void openLinkSubstitutesRowFieldsAndLeavesPlainUrlsAlone() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("file", "models/vault.hdv");
    values.put("element", "hub_customer");
    DrawnContext context = new DrawnContext(List.<HColumn>of(), "hub_customer", values);
    HInteractionAction action =
        new HInteractionAction(
            HInteractionAction.ActionType.OPEN_LINK_SAME_TAB,
            "hop-edw://open?file={file}&element={element}&verb=select");
    assertEquals(
        "hop-edw://open?file=models/vault.hdv&element=hub_customer&verb=select",
        action.resolveObjectName(context));

    HInteractionAction plain =
        new HInteractionAction(
            HInteractionAction.ActionType.OPEN_LINK_SAME_TAB, "https://example.com/a{0}");
    assertEquals("https://example.com/a{0}", plain.resolveObjectName(context));

    HInteractionAction missing =
        new HInteractionAction(HInteractionAction.ActionType.OPEN_LINK_SAME_TAB, "go/{absent}");
    assertEquals("go/", missing.resolveObjectName(context));
  }
}
