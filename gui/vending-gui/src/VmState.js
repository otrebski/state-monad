import React, {Component} from 'react';
import "./VmState.css"

class VmState extends Component {


    createTable = (quantity) => {
        let sorted = quantity.sort((a, b) => (a.code > b.code) ? 1 : -1);
        return sorted.map((value, index) => {
            let quantity = Math.min(10, value.quantity);
            let c = value.symbol.repeat(quantity);
            return <div key={index} className="box">
                <span className={"circle"}>&nbsp;{value.code} </span>&nbsp;
                <span className="price">{value.price}PLN</span>
                <span className="quantity"> {c} </span>
            </div>
        });
    };

    render() {
        return (
            <div>
                <h2>Products</h2>
                {this.createTable(this.props.vmState.quantity)}
            </div>
        )
    }
}

export default VmState