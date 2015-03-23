/*
 *  Copyright (c) 2012-2015 Malhar, Inc.
 *  All Rights Reserved.
 */

package com.datatorrent.demos.twitter.schemas;

import com.datatorrent.lib.appdata.qr.DataType;
import com.datatorrent.lib.appdata.qr.Query;
import com.datatorrent.lib.appdata.qr.Result;
import com.datatorrent.lib.appdata.qr.DataSerializerInfo;
import com.datatorrent.lib.appdata.qr.SimpleDataSerializer;
import com.datatorrent.lib.appdata.schemas.SchemaData;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Set;

/**
 *
 * @author Timothy Farkas: tim@datatorrent.com
 */

@DataType(type=TwitterSchemaResult.TYPE)
@DataSerializerInfo(clazz=SimpleDataSerializer.class)
public class TwitterSchemaResult extends Result
{
  public static final String TYPE = "schemaData";

  public static final String SCHEMA_TYPE = "twitterTop10";
  public static final String SCHEMA_VERSION = "1.0";

  public static final String URL = "url";
  public static final String URL_TYPE = "url";
  public static final String COUNT = "count";
  public static final String COUNT_TYPE = "integer";

  public static final Set<String> FIELDS = ImmutableSet.of(URL, COUNT);

  private TwitterSchemaData data;

  public TwitterSchemaResult(Query query)
  {
    super(query);
    data = new TwitterSchemaData();
    List<SchemaValues> schemaValues = Lists.newArrayList();

    SchemaValues svs = new SchemaValues();
    svs.setName(URL);
    svs.setType(URL_TYPE);

    schemaValues.add(svs);

    svs = new SchemaValues();
    svs.setName(COUNT);
    svs.setType(COUNT_TYPE);

    schemaValues.add(svs);

    data.setSchemaType(SCHEMA_TYPE);
    data.setSchemaVersion(SCHEMA_VERSION);

    data.setValues(schemaValues);
  }

  /**
   * @return the data
   */
  public TwitterSchemaData getData()
  {
    return data;
  }

  /**
   * @param data the data to set
   */
  public void setData(TwitterSchemaData data)
  {
    this.data = data;
  }

  public static class TwitterSchemaData extends SchemaData
  {
    private List<SchemaValues> values;

    public TwitterSchemaData()
    {
    }

    /**
     * @return the values
     */
    public List<SchemaValues> getValues()
    {
      return values;
    }

    /**
     * @param values the values to set
     */
    public void setValues(List<SchemaValues> values)
    {
      this.values = values;
    }
  }
}