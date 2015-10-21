/*
 * Copyright 2015 Collective, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

"use strict";


var _internalSidebarData = Immutable.Map();

var SidebarStore = Object.assign({}, EventEmitter.prototype, {

    /**
     * Get the entire collection of TODOs.
     * @return {object}
     */
    getAll: function () {
        return _internalSidebarData
    }

});

//dispatcherIndex: .bind(this))

AppDispatcher.register(function (payload) {

    console.log("dispatcherIndex: AppDispatcher.register", payload.action);

    switch (payload.source) {
        case TodoConstants.SIDEBAR_UPDATE:
            console.log("case TodoConstants.SIDEBAR_UPDATE");
            _internalSidebarData = _internalSidebarData.setIn(["selected"], Immutable.Map({
                ts: payload.action.ts,
                workflowName: payload.action.workflowName}));
            SidebarStore.emit(CHANGE_EVENT);
            break;

        // add more cases for other actionTypes, like TODO_DESTROY, etc.
    }

    return true; // No errors. Needed by promise in Dispatcher.
});