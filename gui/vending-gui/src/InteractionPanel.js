import React, {Component} from 'react';
import Panel from "./Panel";
import Money from "./Money";
import "./InteractionPanel.css"

class InteractionPanel extends Component {

    render() {
        return <div className="row">
            <div className="column">
                <Panel type={this.props.type}/>
            </div>
            <div className="column">
                <Money type={this.props.type}/>
            </div>
        </div>
    }

}

export default InteractionPanel