import React, {Component} from 'react';
import VmState from "./VmState";
import './App.css';
import InteractionPanel from "./InteractionPanel";
import LcdDisplay from "./LcdDisplay";

class App extends Component {

    constructor(props) {
        super(props);
        this.eventSource = new EventSource("/api/sm/1/events");
        this.state = {
            message: "Loading ...",
            vmState: {
                credit: 0,
                income: 0,
                quantity: [],
                reportedExpiryDate: [],
                reportedShortage: []
            }

        };
        fetch("/api/sm/1/status")
            .then((response) => response.json())
            .then(state => this.setState({vmState: state, message: "Hello"}))
    }


    componentDidMount() {
        this.eventSource.onmessage = e => {
            if (e.data.length > 0) {
                this.updateState(JSON.parse(e.data));
            }
        };
        this.eventSource.onerror = e => console.log("Error", e)

    }

    updateState(event) {
        let messageType = event.messageType;
        if (messageType === "DisplayV1") {
            this.setState({vmState: event.vendingMachineStateV1});
        } else if (messageType === "CreditInfoV1") {
            this.setState({message: "Your credit is " + event.value});
        } else if (messageType === "GiveProductAndChangeV1") {
            this.setState({message: "Take your product and " + event.change + " of change"}); //code, change
        } else if (messageType === "CollectYourMoneyV1") {
            this.setState({message: "Collect your money"});
        } else if (messageType === "WrongProductV1") {
            this.setState({message: "Wrong selection"});
        } else if (messageType === "NotEnoughOfCreditV1") {
            this.setState({message: "Not enough of credit, insert " + event.diff});
        }

    }


    render() {
        return (
            <div>
                <div>
                    <h1>Vending machine</h1>
                    {/*<h2 style={{color: "black", background: "green"}}>LCD: {this.state.message}</h2>*/}
                    <LcdDisplay message={this.state.message}/>
                    <InteractionPanel/>
                    <VmState vmState={this.state.vmState}/>
                </div>
            </div>
        );
    }
}

export default App;
