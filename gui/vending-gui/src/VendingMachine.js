import React, {Component} from 'react';
import "./VmState.css"
import LcdDisplay from "./LcdDisplay";
import InteractionPanel from "./InteractionPanel";
import VmState from "./VmState";

class VendingMachine extends Component {

    render() {
        return (
            <div>
                <div>
                    <LcdDisplay message={this.props.message}/>
                    <InteractionPanel type={this.props.type}/>
                    <VmState vmState={this.props.vmState}/>
                </div>
            </div>
        );
    }

}

export default VendingMachine