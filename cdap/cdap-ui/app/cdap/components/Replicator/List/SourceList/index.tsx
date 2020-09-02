/*
 * Copyright © 2020 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

import * as React from 'react';
import withStyles, { WithStyles, StyleRules } from '@material-ui/core/styles/withStyles';
import { getCurrentNamespace } from 'services/NamespaceStore';
import { Link } from 'react-router-dom';
import PluginCard, {
  PluginCardWidth,
  PluginCardHeight,
} from 'components/Replicator/List/PluginCard';
import HorizontalCarousel from 'components/HorizontalCarousel';
import { fetchPluginsAndWidgets } from 'components/Replicator/utilities';
import { PluginType } from 'components/Replicator/constants';
import TextField from '@material-ui/core/TextField';
import InputAdornment from '@material-ui/core/InputAdornment';
import Search from '@material-ui/icons/Search';
import { objectQuery } from 'services/helpers';
import If from 'components/If';
import LoadingSVG from 'components/LoadingSVG';
import { MyReplicatorApi } from 'api/replicator';

const styles = (): StyleRules => {
  return {
    header: {
      display: 'grid',
      gridTemplateColumns: '1fr 400px',
    },
    searchSection: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'flex-end',
    },
    link: {
      marginRight: '25px',

      '&:hover': {
        textDecoration: 'none',
      },
    },
    search: {
      width: '200px',
      marginLeft: '20px',

      '& input': {
        paddingTop: '10px',
        paddingBottom: '10px',
      },
    },
    listContainer: {
      marginTop: '15px',
      height: `${PluginCardHeight}px`,
    },
    loadingContainer: {
      width: '100%',
      height: '100%',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
    },
    arrow: {
      top: `${Math.floor(PluginCardHeight / 2)}px`,
    },
  };
};

const SourceListView: React.FC<WithStyles<typeof styles>> = ({ classes }) => {
  const [sources, setSources] = React.useState([]);
  const [widgetMap, setWidgetMap] = React.useState({});
  const [search, setSearch] = React.useState('');
  const [loading, setLoading] = React.useState(true);

  React.useEffect(() => {
    MyReplicatorApi.getDeltaApp().subscribe((appInfo) => {
      fetchPluginsAndWidgets(appInfo.artifact, PluginType.source).subscribe((res) => {
        setSources(res.plugins);
        setWidgetMap(res.widgetMap);
        setLoading(false);
      });
    });
  }, []);

  function handleSearch(e) {
    const value = e.target.value;
    setSearch(value);
  }

  let filteredSources = sources;
  if (search.length > 0) {
    filteredSources = sources.filter((source) => {
      const pluginKey = `${source.name}-${source.type}`;
      const displayName = objectQuery(widgetMap, pluginKey, 'display-name');

      const normalizedSearch = search.toLowerCase();

      if (source.name.toLowerCase().indexOf(normalizedSearch) !== -1) {
        return true;
      }

      return displayName && displayName.toLowerCase().indexOf(normalizedSearch) !== -1;
    });
  }

  return (
    <div>
      <div className={classes.header}>
        <div>
          <h4>Create new replication pipeline</h4>
          <div>Start by selecting the source from where you want to replicate your data</div>
        </div>
        <div className={classes.searchSection}>
          <div>
            {sources.length} {sources.length === 1 ? 'source' : 'sources'} available
          </div>
          <TextField
            className={classes.search}
            value={search}
            onChange={handleSearch}
            variant="outlined"
            placeholder="Search sources by name"
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <Search />
                </InputAdornment>
              ),
            }}
          />
        </div>
      </div>

      <div className={classes.listContainer}>
        <If condition={loading}>
          <div className={classes.loadingContainer}>
            <LoadingSVG />
          </div>
        </If>
        <If condition={!loading}>
          <HorizontalCarousel scrollAmount={PluginCardWidth} classes={{ arrow: classes.arrow }}>
            {filteredSources.map((source) => {
              const { name: artifactName, version, scope } = source.artifact;
              const pluginKey = `${source.name}-${source.type}`;
              const widgetInfo = widgetMap[pluginKey];

              const sourceName = widgetInfo ? widgetInfo['display-name'] : source.name;
              const icon = objectQuery(widgetInfo, 'icon', 'arguments', 'data');

              return (
                <Link
                  key={source.name}
                  className={classes.link}
                  to={`/ns/${getCurrentNamespace()}/replication/create/${artifactName}/${version}/${scope}/${
                    source.name
                  }`}
                >
                  <PluginCard name={sourceName} icon={icon} />
                </Link>
              );
            })}
          </HorizontalCarousel>
        </If>
      </div>
    </div>
  );
};

const SourceList = withStyles(styles)(SourceListView);
export default SourceList;
