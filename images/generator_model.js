var d3 = require('d3');
var fs = require('fs')
var xmldom = require('xmldom')

function generateModel(model) {
  var el = 'body';
  d3.select(el)[0][0].innerHTML = '';
  require('./draw_model.js').draw_model(d3, el, model);

  // get a reference to our SVG object and add the SVG NS
  var svgGraph = d3.select('svg')
    .attr('xmlns', 'http://www.w3.org/2000/svg')
    .attr('xmlns:xmlns:xlink', 'http://www.w3.org/1999/xlink');

  var svgXML = d3.select(el)[0][0].innerHTML;
  var pd = require('pretty-data').pd;

  fs.writeFile('tmp/model-' + model + '.svg', pd.xml(svgXML));
}

generateModel('T');
generateModel('B');
generateModel('F');
generateModel('L');
generateModel('$');
