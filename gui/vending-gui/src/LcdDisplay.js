import React, {Component} from 'react';
import "./LcdDisplay.css"

class LcdDisplay extends Component {

    render() {
        return <div className="textbox">{this.props.message}</div>
    }

}

export default LcdDisplay