import React, {Component} from 'react';
import Panel from "./Panel";
import Money from "./Money";
import "./InteractionPanel.css"

class InteractionPanel extends Component {

    render() {
        return <div className="row">
            <div className="column">
                <Panel/>
            </div>
            <div className="column">
                <Money/>
            </div>
        </div>
    }

}

export default InteractionPanel