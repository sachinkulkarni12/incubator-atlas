/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.atlas.util;

public class AtlasGremlin2QueryProvider extends AtlasGremlinQueryProvider {
    @Override
    public String getQuery(final AtlasGremlinQuery gremlinQuery) {
        switch (gremlinQuery) {
            case TYPE_COUNT_METRIC:
                return "g.V().has('__type', 'typeSystem').filter({it.'__type.category'.name() != 'TRAIT'}).count()";
            case TYPE_UNUSED_COUNT_METRIC:
                return "g.V('__type', 'typeSystem').filter({ it.'__type.category'.name() != 'TRAIT' && it.inE.count() == 0}).count()";
            case ENTITY_COUNT_METRIC:
                return "g.V().has('__superTypeNames', T.in, ['Referenceable']).count()";
            case TAG_COUNT_METRIC:
                return "g.V().has('__type', 'typeSystem').filter({it.'__type.category'.name() == 'TRAIT'}).count()";
            case ENTITY_DELETED_METRIC:
                return "g.V().has('__superTypeNames', T.in, ['Referenceable']).has('__status', 'DELETED').count()";
            case ENTITIES_PER_TYPE_METRIC:
                return "g.V().has('__typeName', T.in, g.V().has('__type', 'typeSystem').filter({it.'__type.category'.name() != 'TRAIT'}).'__type.name'.toSet()).groupCount{it.'__typeName'}.cap.toList()";
            case TAGGED_ENTITIES_METRIC:
                return "g.V().has('__superTypeNames', T.in, ['Referenceable']).has('__traitNames').count()";
            case ENTITIES_FOR_TAG_METRIC:
                return "g.V().has('__typeName', T.in, g.V().has('__type', 'typeSystem').filter{it.'__type.category'.name() == 'TRAIT'}.'__type.name'.toSet()).groupCount{it.'__typeName'}.cap.toList()";
            case EXPORT_BY_GUID:
                return "g.V('__guid', startGuid).bothE().bothV().has('__guid').__guid.dedup().toList()";
            case EXPORT_TYPE_STARTS_WITH:
                return "g.V().has('__typeName','%s').filter({it.'%s'.startsWith(attrValue)}).has('__guid').__guid.toList()";
            case EXPORT_TYPE_ENDS_WITH:
                return "g.V().has('__typeName','%s').filter({it.'%s'.endsWith(attrValue)}).has('__guid').__guid.toList()";
            case EXPORT_TYPE_CONTAINS:
                return "g.V().has('__typeName','%s').filter({it.'%s'.contains(attrValue)}).has('__guid').__guid.toList()";
            case EXPORT_TYPE_MATCHES:
                return "g.V().has('__typeName','%s').filter({it.'%s'.matches(attrValue)}).has('__guid').__guid.toList()";
            case EXPORT_TYPE_DEFAULT:
                return "g.V().has('__typeName','%s').has('%s', attrValue).has('__guid').__guid.toList()";
            case FULL_LINEAGE:
                return "g.V('__guid', '%s').as('src').in('%s').out('%s')." +
                        "loop('src', {((it.path.contains(it.object)) ? false : true)}, " +
                        "{((it.object.'__superTypeNames') ? " +
                        "(it.object.'__superTypeNames'.contains('DataSet')) : false)})." +
                        "path().toList()";
            case PARTIAL_LINEAGE:
                return "g.V('__guid', '%s').as('src').in('%s').out('%s')." +
                        "loop('src', {it.loops <= %s}, {((it.object.'__superTypeNames') ? " +
                        "(it.object.'__superTypeNames'.contains('DataSet')) : false)})." +
                        "path().toList()";
        }
        // Should never reach this point
        return null;
    }

}
