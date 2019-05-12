import React, {Component} from 'react';
import "./Panel.css"

class Panel extends Component {

    selectProduct = product => {
        let url = "/api/" + this.props.type + "/0/select/" + product;
        fetch(url)
    };

    createTable = () => {
        let table = [];
        // Outer loop to create parent
        for (let i = 1; i < 10; i++) {
            table.push(<button className="panelButton" onClick={() => this.selectProduct(i)}>{i}</button>);
            if (i % 3 === 0) {
                table.push(<br/>);
            }
        }
        table.push(<button className="panelButton" onClick={() => fetch("/api/" + this.props.type + "/0/withdrawn")}>C</button>);
        return table
    };


    render() {
        return <div>{this.createTable()}</div>
    }

}

export default Panel