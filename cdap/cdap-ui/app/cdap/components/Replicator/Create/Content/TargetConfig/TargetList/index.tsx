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
import { createContextConnect, ICreateContext } from 'components/Replicator/Create';
import HorizontalCarousel from 'components/HorizontalCarousel';
import { fetchPluginsAndWidgets } from 'components/Replicator/utilities';
import { PluginType } from 'components/Replicator/constants';
import { objectQuery } from 'services/helpers';
import TextField from '@material-ui/core/TextField';
import InputAdornment from '@material-ui/core/InputAdornment';
import Search from '@material-ui/icons/Search';
import PluginCard, {
  PluginCardWidth,
  PluginCardHeight,
} from 'components/Replicator/List/PluginCard';
import classnames from 'classnames';
import Heading, { HeadingTypes } from 'components/Heading';

const styles = (theme): StyleRules => {
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
    targetItem: {
      marginRight: '25px',
      cursor: 'pointer',
    },
    selected: {
      borderColor: theme.palette.blue[200],
      borderWidth: '3px',
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
      marginBottom: '15px',
      height: `${PluginCardHeight}px`,
    },
    arrow: {
      top: `${Math.floor(PluginCardHeight / 2)}px`,
    },
  };
};

interface ITargetListProps extends ICreateContext, WithStyles<typeof styles> {
  onSelect: (target) => void;
  currentSelection: any;
}

const TargetListView: React.FC<ITargetListProps> = ({
  classes,
  onSelect,
  currentSelection,
  parentArtifact,
}) => {
  const [targets, setTargets] = React.useState([]);
  const [widgetMap, setWidgetMap] = React.useState({});
  const [search, setSearch] = React.useState('');

  React.useEffect(() => {
    fetchPluginsAndWidgets(parentArtifact, PluginType.target).subscribe((res) => {
      if (res.plugins.length === 1 && !currentSelection) {
        onSelect(res.plugins[0]);
      }

      setTargets(res.plugins);
      setWidgetMap(res.widgetMap);
    });
  }, []);

  function handleSearch(e) {
    const value = e.target.value;
    setSearch(value);
  }

  let filteredTarget = targets;
  if (search.length > 0) {
    filteredTarget = targets.filter((target) => {
      const pluginKey = `${target.name}-${target.type}`;
      const displayName = objectQuery(widgetMap, pluginKey, 'display-name');

      const normalizedSearch = search.toLowerCase();

      if (target.name.toLowerCase().indexOf(normalizedSearch) !== -1) {
        return true;
      }

      return displayName && displayName.toLowerCase().indexOf(normalizedSearch) !== -1;
    });
  }

  return (
    <div className={classes.root}>
      <div className={classes.header}>
        <div>
          <Heading type={HeadingTypes.h4} label="Select target" />
        </div>
        <div className={classes.searchSection}>
          <div>
            {targets.length} {targets.length === 1 ? 'source' : 'sources'} available
          </div>
          <TextField
            className={classes.search}
            value={search}
            onChange={handleSearch}
            variant="outlined"
            placeholder="Search targets by name"
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
        <HorizontalCarousel scrollAmount={PluginCardWidth} classes={{ arrow: classes.arrow }}>
          {filteredTarget.map((target) => {
            const pluginKey = `${target.name}-${target.type}`;
            const widgetInfo = widgetMap[pluginKey];
            const targetName = widgetInfo ? widgetInfo['display-name'] : target.name;
            const icon = objectQuery(widgetInfo, 'icon', 'arguments', 'data');

            const currentSelectionName = objectQuery(currentSelection, 'name');
            const currentSelectionArtifact = objectQuery(currentSelection, 'artifact', 'name');

            return (
              <div
                key={target.name}
                className={classes.targetItem}
                onClick={onSelect.bind(null, target)}
              >
                <PluginCard
                  name={targetName}
                  icon={icon}
                  classes={{
                    root: classnames({
                      [classes.selected]:
                        target.name === currentSelectionName &&
                        target.artifact.name === currentSelectionArtifact,
                    }),
                  }}
                />
              </div>
            );
          })}
        </HorizontalCarousel>
      </div>
    </div>
  );
};

const StyledTargetList = withStyles(styles)(TargetListView);
const TargetList = createContextConnect(StyledTargetList);
export default TargetList;
