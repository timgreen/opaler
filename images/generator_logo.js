var d3 = require('d3');
var fs = require('fs')
var xmldom = require('xmldom')
var alphaBeta = process.argv[2];

function getSvg(path) {
  var trainSvgString = fs.readFileSync(path).toString();
  var trainSvgDom = new xmldom.DOMParser().parseFromString(trainSvgString, 'image/svg+xml');
  var trainSvg = '';
  var childNodes = trainSvgDom.getElementsByTagName('svg')[0].childNodes;
  for (var i = 0; i < childNodes.length; i++) {
    var node = childNodes[i];
    trainSvg += (new xmldom.XMLSerializer).serializeToString(node);
  }
  return trainSvg
}

function generateLogo() {
  var el = 'body';
  var inlinedTrain = getSvg('input/aiga_railtransportion_transparent.svg');
  var inlindeSmallIcon = null;
  var inlinedBadge = false;
  if (alphaBeta) {
    inlinedBadge = getSvg('input/icon/' + alphaBeta + '.svg')
  }
  require('./draw_logo.js').draw_logo(d3, el, inlinedTrain, inlinedBadge);

  // get a reference to our SVG object and add the SVG NS
  var svgGraph = d3.select('svg')
    .attr('xmlns', 'http://www.w3.org/2000/svg')
    .attr('xmlns:xmlns:xlink', 'http://www.w3.org/1999/xlink');

  var svgXML = d3.select(el)[0][0].innerHTML;
  var pd = require('pretty-data').pd;

  fs.writeFile('tmp/logo' + (alphaBeta? '-' + alphaBeta : '') + '.svg', pd.xml(svgXML));
}

generateLogo();
