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
package org.hopper.presentation.component.types.table;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.core.HColumn;
import org.hopper.core.HEnvironment;
import org.hopper.core.HFont;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HVerticalAlignment;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.memory.HInMemoryRowsConnector;
import org.hopper.presentation.datacontext.PresentationDataContext;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.page.HPage;
import org.hopper.render.context.SimpleRenderContext;
import org.hopper.util.BasePresentationUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HTableComponentRowFieldsTest {

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
    BasePresentationUtil.registerTestPlugins();
  }

  @Test
  void hiddenColumnStaysOnTheRowFieldMap() throws Exception {
    RowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString("name"));
    meta.addValueMeta(new ValueMetaString("link"));
    List<RowMetaAndData> rows =
        List.of(new RowMetaAndData(meta, new Object[] {"hub_customer", "hop-edw://open?element=hub_customer"}));
    HInMemoryRowsConnector memory = new HInMemoryRowsConnector(rows);
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    provider.getSerializer(HConnector.class).save(new HConnector("rows", memory));

    HColumn name =
        new HColumn("name", "Name", HHorizontalAlignment.LEFT, HVerticalAlignment.MIDDLE);
    name.setWidth(0);
    HTableComponent table = new HTableComponent();
    table.setHeader(true);
    table.setHorizontalMargin(1);
    table.setVerticalMargin(1);
    table.setSourceConnectorName("rows");
    table.setDefaultFont(new HFont("SansSerif", "12", false, false));
    table.setHeaderFont(new HFont("SansSerif", "12", true, false));
    table.setColumnSelection(new ArrayList<>(List.of(name)));

    HPresentation presentation = new HPresentation();
    presentation.setName("row-fields");
    HPage page = HPage.getA4(false);
    HComponent wrapper = new HComponent("T", table);
    page.getComponents().add(wrapper);
    presentation.getPages().add(page);

    HLayoutResults results = new HLayoutResults(null);
    table.processSourceData(
        presentation,
        page,
        wrapper,
        new PresentationDataContext(presentation, provider),
        new SimpleRenderContext(provider),
        results);
    TableDetails details =
        (TableDetails) results.getDataSet(wrapper, HTableComponent.DATA_TABLE_DETAILS);
    assertEquals("hop-edw://open?element=hub_customer", details.rowFieldValues.get(1).get("link"));
    assertEquals("hub_customer", details.rowFieldValues.get(1).get("name"));
  }
}
